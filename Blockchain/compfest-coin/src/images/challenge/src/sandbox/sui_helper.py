from __future__ import annotations

import fcntl
import json
import logging
import os
import shutil
import signal
import socket
import subprocess
import tempfile
import threading
import time
from pathlib import Path
from typing import Any
from uuid import uuid4

import requests

from .type import AccountInfo, NodeInfo


logger = logging.getLogger("BlockchainGateway")

SUI_GAS_BUDGET = int(os.getenv("SUI_GAS_BUDGET", "100000000"))
SUI_BUILD_ENV = os.getenv("SUI_BUILD_ENV", "testnet")
SUI_STATE_ROOT = Path(os.getenv("SUI_STATE_ROOT", "/tmp/sui-instances"))
SUI_POOL_ROOT = Path(os.getenv("SUI_POOL_ROOT", str(SUI_STATE_ROOT / "pool")))
SUI_POOL_SIZE = int(os.getenv("SUI_POOL_SIZE", "0"))
SUI_POOL_REFILL_INTERVAL = float(os.getenv("SUI_POOL_REFILL_INTERVAL", "5"))
SUI_ISOLATION_MODE = os.getenv("SUI_ISOLATION_MODE", "private_pool")
SUI_MAX_ACTIVE = int(os.getenv("SUI_MAX_ACTIVE", "0"))

_POOL_THREAD_STARTED = False
_POOL_THREAD_LOCK = threading.Lock()


def publish_package(client_config: str, package_path: str, *, gas_budget: int | None = None) -> str:
    budget = gas_budget or SUI_GAS_BUDGET
    result = subprocess.run(
        [
            "sui", "client", "--client.config", client_config,
            "--json", "test-publish", "--build-env", SUI_BUILD_ENV,
            "--gas-budget", str(budget),
            package_path,
        ],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120,
        cwd=package_path,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"sui client publish failed: code={result.returncode}: "
            f"stdout={result.stdout[-2000:]} stderr={result.stderr[-2000:]}"
        )
    data = json.loads(result.stdout)
    if "effects" in data and "created" in data["effects"]:
        for obj in data["effects"]["created"]:
            if obj.get("owner") == "Immutable":
                pkg_id = obj["reference"]["objectId"]
                if pkg_id.startswith("0x"):
                    return pkg_id
    if "objectChanges" in data:
        for change in data["objectChanges"]:
            if change.get("type") == "published":
                return change["packageId"]
    raise RuntimeError("could not determine package ID from publish output")


def call_function(
    client_config: str,
    package_id: str,
    module: str,
    function: str,
    *,
    args: list[str] | None = None,
    gas_budget: int | None = None,
) -> dict:
    budget = gas_budget or SUI_GAS_BUDGET
    cmd = [
        "sui", "client", "--client.config", client_config,
        "--json", "call",
        "--package", package_id,
        "--module", module,
        "--function", function,
        "--gas-budget", str(budget),
    ]
    if args:
        for a in args:
            cmd.extend(["--args", a])
    result = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
    if result.returncode != 0:
        raise RuntimeError(
            f"sui client call failed: {package_id}::{module}::{function}: "
            f"stdout={result.stdout[-2000:]} stderr={result.stderr[-2000:]}"
        )
    return json.loads(result.stdout)


def get_object_id_by_type(
    rpc_url: str, owner_addr: str, package_id: str, module: str, struct: str
) -> str | None:
    response = requests.post(
        rpc_url,
        json={
            "jsonrpc": "2.0",
            "id": 1,
            "method": "suix_getOwnedObjects",
            "params": [
                owner_addr,
                {
                    "filter": {"StructType": f"{package_id}::{module}::{struct}"},
                    "options": {"showType": True},
                },
            ],
        },
        timeout=30,
    )
    response.raise_for_status()
    data = response.json()
    if "error" in data:
        raise RuntimeError(f"RPC error: {data['error']}")
    objects = data.get("result", {}).get("data", [])
    if objects:
        return objects[0]["data"]["objectId"]
    return None


_ADMIN_CAPS: dict[tuple[str, str], str] = {}


def _owner_kind(change: dict) -> str:
    owner = change.get("owner")
    if owner == "Immutable":
        return "Immutable"
    if isinstance(owner, dict):
        if "Shared" in owner:
            return "Shared"
        if "AddressOwner" in owner:
            return "AddressOwner"
    return ""


def find_object(
    tx_result: dict,
    package_id: str,
    module: str,
    struct: str,
    *,
    shared: bool = False,
    owned: bool = False,
) -> str | None:
    expected = f"{package_id}::{module}::{struct}"
    want = "Shared" if shared else "AddressOwner" if owned else None
    for change in tx_result.get("objectChanges", []):
        object_type = change.get("objectType", "")
        if object_type != expected and not object_type.startswith(expected + "<"):
            continue
        if want and _owner_kind(change) != want:
            continue
        return change.get("objectId")
    return None


def _rpc_of(client_config: str) -> str:
    for line in Path(client_config).read_text().splitlines():
        line = line.strip()
        if line.startswith("rpc:"):
            return line.split("rpc:", 1)[1].strip().strip('"')
    raise RuntimeError(f"no rpc url in {client_config}")


def admin_cap(admin_config: str, package_id: str, admin_address: str) -> str:
    key = (admin_config, package_id)
    # Memoised: looked up on every launch, but never changes for a given
    # admin and package.
    if key not in _ADMIN_CAPS:
        cap = get_object_id_by_type(
            _rpc_of(admin_config), admin_address, package_id, "setup", "AdminCap"
        )
        if not cap:
            raise RuntimeError(f"no setup::AdminCap owned by {admin_address}")
        _ADMIN_CAPS[key] = cap
    return _ADMIN_CAPS[key]


publish = publish_package
call = call_function


def encode_contract_addr(value: dict | str) -> str:
    # NodeInfo.contract_addr is always a JSON string on the wire: every
    # consumer (e.g. launcher.generate_session_data) does json.loads on it.
    # A challenge's deploy() returns a dict, so callers must route it through
    # here before assigning it, never store the dict itself.
    if isinstance(value, dict):
        return json.dumps(value)
    if isinstance(value, str):
        return value
    raise TypeError(f"contract_addr must be a dict or str, got {type(value).__name__}")


def find_created_object(tx_result: dict) -> str | None:
    for change in tx_result.get("objectChanges", []):
        owner = change.get("owner", {})
        if isinstance(owner, dict) and "Shared" in owner:
            return change.get("objectId") or change.get("objectID")
    effects = tx_result.get("effects", {})
    for obj in effects.get("created", []):
        owner = obj.get("owner", {})
        if isinstance(owner, dict) and "Shared" in owner:
            ref = obj.get("reference", {})
            return ref.get("objectId") or ref.get("objectID")
    return None


def check_solved(rpc_url: str, setup_id: str) -> bool:
    try:
        response = requests.post(
            rpc_url,
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "method": "sui_getObject",
                "params": [setup_id, {"showContent": True}],
            },
            timeout=30,
        )
        response.raise_for_status()
        data = response.json()
        if "error" in data:
            return False
        content = data.get("result", {}).get("data", {}).get("content", {})
        fields = content.get("fields", {})
        if "solved" not in fields:
            logger.error(
                "setup object %s has no `solved` field; available: %s",
                setup_id, sorted(fields),
            )
            return False
        return fields["solved"] is True
    except Exception:
        return False


def fund_account(faucet_url: str, address: str) -> None:
    faucet_url = faucet_url.rstrip("/")
    response = requests.post(
        faucet_url + "/gas",
        json={"FixedAmountRequest": {"recipient": address}},
        timeout=30,
    )
    response.raise_for_status()


def generate_key() -> dict[str, str]:
    result = subprocess.run(
        ["sui", "keytool", "generate", "ed25519", "--json"],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30,
    )
    if result.returncode != 0:
        raise RuntimeError(f"sui keytool generate failed: {result.stderr}")
    return json.loads(result.stdout)


def write_client_config(path: str, rpc_url: str, active_address: str | None, keystore_path: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    active_line = f'active-address: "{active_address}"' if active_address else "active-address: ~"
    Path(path).write_text(
        "\n".join([
            "---",
            f"keystore:",
            f'  File: {keystore_path}',
            "envs:",
            "  - alias: local",
            f'    rpc: "{rpc_url}"',
            "    ws: ~",
            "    basic_auth: ~",
            "active_env: local",
            active_line,
            "",
        ])
    )


def run_checked(args: list[str], *, timeout: int = 120, cwd: str | None = None, env: dict[str, str] | None = None) -> str:
    result = subprocess.run(
        args,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        cwd=cwd,
        env=env,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"command failed: {args}: code={result.returncode}: "
            f"stdout={result.stdout[-2000:]} stderr={result.stderr[-2000:]}"
        )
    return result.stdout.strip()


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def wait_tcp(host: str, port: int, timeout: int = 120) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            with socket.create_connection((host, port), timeout=2):
                return
        except Exception as exc:
            last_error = exc
        time.sleep(0.25)
    raise TimeoutError(f"tcp endpoint did not become ready: {host}:{port}: {last_error}")


def wait_for_rpc(rpc_url: str, timeout: int = 120) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            response = requests.post(
                rpc_url,
                json={"jsonrpc": "2.0", "id": 1, "method": "sui_getLatestCheckpointSequenceNumber", "params": []},
                timeout=2,
            )
            if response.ok:
                response.json()
                return
        except Exception:
            pass
        time.sleep(1)
    raise TimeoutError(f"sui RPC did not become ready: {rpc_url}")


def generate_sui_network_config(network_config_dir: Path, rpc_url: str, rpc_port: int) -> None:
    network_config_dir.mkdir(parents=True, exist_ok=True)
    run_checked(
        ["sui", "genesis", "--working-dir", str(network_config_dir), "--with-faucet", "--force"],
        timeout=120,
    )

    client_config = network_config_dir / "client.yaml"
    if client_config.exists():
        text = client_config.read_text()
        text = text.replace('rpc: "http://127.0.0.1:9000"', f'rpc: "{rpc_url}"', 1)
        client_config.write_text(text)

    fullnode_config = network_config_dir / "fullnode.yaml"
    if fullnode_config.exists():
        text = fullnode_config.read_text()
        text = text.replace(
            'json-rpc-address: "0.0.0.0:9000"',
            f'json-rpc-address: "0.0.0.0:{rpc_port}"',
            1,
        )
        fullnode_config.write_text(text)


def start_sui_process(seed_data: dict[str, Any], *, regenesis: bool = False) -> int:
    log_dir = Path(seed_data["log_dir"])
    log_dir.mkdir(parents=True, exist_ok=True)

    sui_home = Path(seed_data["runtime_home"])
    sui_config_dir = sui_home / ".sui" / "sui_config"
    sui_config_dir.mkdir(parents=True, exist_ok=True)

    command = [
        "sui",
        "start",
        "--network.config",
        seed_data["network_config_dir"],
        f"--with-faucet=0.0.0.0:{seed_data['faucet_port']}",
        "--fullnode-rpc-port",
        str(seed_data["rpc_port"]),
    ]
    if regenesis:
        command.insert(2, "--force-regenesis")

    env = {
        **os.environ,
        "HOME": str(sui_home),
        "SUI_CONFIG_DIR": str(sui_config_dir),
    }

    with open(log_dir / "sui.stdout.log", "ab") as stdout, open(log_dir / "sui.stderr.log", "ab") as stderr:
        proc = subprocess.Popen(command, stdout=stdout, stderr=stderr, start_new_session=True, env=env)

    try:
        wait_for_rpc(seed_data["rpc_url"], timeout=120)
        wait_tcp("127.0.0.1", int(seed_data["faucet_port"]), timeout=120)
    except Exception:
        terminate_pid_tree(proc.pid)
        raise

    return int(proc.pid)


def terminate_pid_tree(pid: int | str | None, *, timeout: float = 3) -> None:
    if pid is None:
        return
    try:
        pid_int = int(pid)
    except (TypeError, ValueError):
        return

    try:
        pgid = os.getpgid(pid_int)
        os.killpg(pgid, signal.SIGTERM)
    except ProcessLookupError:
        return
    except Exception:
        try:
            os.kill(pid_int, signal.SIGTERM)
        except Exception:
            return

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            os.kill(pid_int, 0)
        except ProcessLookupError:
            return
        time.sleep(0.1)

    try:
        os.killpg(os.getpgid(pid_int), signal.SIGKILL)
    except Exception:
        try:
            os.kill(pid_int, signal.SIGKILL)
        except Exception:
            pass


def create_sui_keypair(label: str) -> dict[str, str]:
    with tempfile.TemporaryDirectory(prefix=f"sui-key-{label}-") as tmp_dir:
        output = run_checked(
            ["sui", "keytool", "generate", "ed25519", "--json"],
            timeout=30,
            cwd=tmp_dir,
        )
        data = json.loads(output)
        address = data["suiAddress"]
        base64_key = (Path(tmp_dir) / f"{address}.key").read_text().strip()

    convert = subprocess.run(
        ["sui", "keytool", "convert", "--json", base64_key],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=30,
    )
    if convert.returncode == 0:
        bech32_key = json.loads(convert.stdout).get("bech32WithFlag", base64_key)
    else:
        bech32_key = base64_key

    return {
        "address": address,
        "base64_key": base64_key,
        "bech32_key": bech32_key,
    }


def fund_account_with_retry(faucet_url: str, address: str, *, attempts: int = 30, delay: float = 1) -> None:
    last_error: Exception | None = None
    for _ in range(attempts):
        try:
            fund_account(faucet_url, address)
            return
        except Exception as exc:
            last_error = exc
            time.sleep(delay)
    raise RuntimeError(f"failed to fund Sui account {address}: {last_error}")


def create_private_sui_node(team_id: str) -> NodeInfo:
    if SUI_ISOLATION_MODE not in {"private", "private_pool"}:
        raise RuntimeError(f"unsupported SUI_ISOLATION_MODE={SUI_ISOLATION_MODE!r}")

    node_uuid = str(uuid4())
    rpc_port = find_free_port()
    faucet_port = find_free_port()

    config_dir = SUI_STATE_ROOT / f"sui-{node_uuid}"
    runtime_home = config_dir / "home"
    network_config_dir = config_dir / "network"
    log_dir = config_dir / "logs"
    rpc_url = f"http://127.0.0.1:{rpc_port}"
    faucet_url = f"http://127.0.0.1:{faucet_port}"

    seed_data: dict[str, Any] = {
        "isolation_mode": SUI_ISOLATION_MODE,
        "rpc_url": rpc_url,
        "faucet_url": faucet_url,
        "rpc_port": rpc_port,
        "faucet_port": faucet_port,
        "config_dir": str(config_dir),
        "runtime_home": str(runtime_home),
        "network_config_dir": str(network_config_dir),
        "log_dir": str(log_dir),
    }

    try:
        generate_sui_network_config(network_config_dir, rpc_url, rpc_port)
        pid = start_sui_process(seed_data)

        admin_key = create_sui_keypair("admin")
        player_key = create_sui_keypair("player")

        admin_config = str(config_dir / "client.yaml")
        admin_keystore = str(config_dir / "sui.keystore")
        player_config = str(config_dir / "player" / "client.yaml")
        player_keystore = str(config_dir / "player" / "sui.keystore")

        Path(admin_keystore).write_text(json.dumps([admin_key["base64_key"]]))
        write_client_config(admin_config, rpc_url, admin_key["address"], admin_keystore)
        Path(player_keystore).parent.mkdir(parents=True, exist_ok=True)
        Path(player_keystore).write_text(json.dumps([player_key["base64_key"]]))
        write_client_config(player_config, rpc_url, player_key["address"], player_keystore)

        fund_account_with_retry(faucet_url, admin_key["address"])

        seed_data.update({
            "admin_config": admin_config,
            "player_config": player_config,
            "pid": pid,
        })

        return NodeInfo(
            port=str(rpc_port),
            accounts=[
                AccountInfo(
                    address=admin_key["address"],
                    private_key=admin_key["bech32_key"],
                    public_key=admin_key["address"],
                ),
                AccountInfo(
                    address=player_key["address"],
                    private_key=player_key["bech32_key"],
                    public_key=player_key["address"],
                ),
            ],
            pid=pid,
            uuid=node_uuid,
            team=team_id,
            seed=json.dumps(seed_data),
        )
    except Exception:
        terminate_pid_tree(seed_data.get("pid"))
        shutil.rmtree(config_dir, ignore_errors=True)
        raise


def terminate_sui_node(node_info: NodeInfo) -> None:
    seed_data = parse_sui_seed(node_info)
    terminate_pid_tree(seed_data.get("pid", node_info.pid))
    remove_pool_record(node_info.uuid)

    config_dir = seed_data.get("config_dir")
    if isinstance(config_dir, str) and config_dir:
        shutil.rmtree(config_dir, ignore_errors=True)


def parse_sui_seed(node_info: NodeInfo) -> dict[str, Any]:
    try:
        seed_data = json.loads(node_info.seed or "{}")
    except (TypeError, json.JSONDecodeError):
        seed_data = {}
    return seed_data if isinstance(seed_data, dict) else {}


def pool_ready_dir() -> Path:
    return SUI_POOL_ROOT / "ready"


def pool_leased_dir() -> Path:
    return SUI_POOL_ROOT / "leased"


def pool_lock_path() -> Path:
    SUI_POOL_ROOT.mkdir(parents=True, exist_ok=True)
    return SUI_POOL_ROOT / "pool.lock"


def ensure_pool_dirs() -> None:
    pool_ready_dir().mkdir(parents=True, exist_ok=True)
    pool_leased_dir().mkdir(parents=True, exist_ok=True)


class FileLock:
    def __init__(self, path: Path):
        self.path = path
        self.fd = None

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.fd = open(self.path, "a")
        fcntl.flock(self.fd.fileno(), fcntl.LOCK_EX)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.fd:
            fcntl.flock(self.fd.fileno(), fcntl.LOCK_UN)
            self.fd.close()


def atomic_write_node_info(path: Path, node_info: NodeInfo) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(f".{path.name}.{os.getpid()}.{threading.get_ident()}.tmp")
    tmp.write_text(json.dumps(node_info.to_dict()))
    tmp.replace(path)


def remove_pool_record(uuid: str) -> None:
    for root in (pool_ready_dir(), pool_leased_dir()):
        try:
            (root / uuid).unlink()
        except FileNotFoundError:
            pass


def pool_counts() -> dict[str, int]:
    ensure_pool_dirs()
    ready = sum(1 for p in pool_ready_dir().iterdir() if p.is_file())
    leased = sum(1 for p in pool_leased_dir().iterdir() if p.is_file())
    return {"ready": ready, "leased": leased, "target": SUI_POOL_SIZE}


def _active_instance_count_soft() -> int:
    """Count active team instances without importing blockchain_manager (cycle-safe)."""
    try:
        root = Path("/tmp/instances-by-uuid")
        return sum(1 for name in os.listdir(root) if len(name) == 36 and name.count("-") == 4)
    except FileNotFoundError:
        return 0


def total_sui_process_budget_ok() -> bool:
    counts = pool_counts()
    active = _active_instance_count_soft()
    max_active = SUI_MAX_ACTIVE if SUI_MAX_ACTIVE > 0 else 10**9
    max_total = max_active + max(SUI_POOL_SIZE, 0)
    return (active + counts["ready"]) < max_total


def reap_pool_orphans() -> int:
    """Drop ready/leased pool records whose RPC is dead; kill processes."""
    if SUI_ISOLATION_MODE not in {"private", "private_pool"}:
        return 0
    ensure_pool_dirs()
    removed = 0
    with FileLock(pool_lock_path()):
        for root in (pool_ready_dir(), pool_leased_dir()):
            for path in list(root.iterdir()):
                if not path.is_file():
                    continue
                try:
                    node_info = NodeInfo(**json.loads(path.read_text()))
                except Exception:
                    path.unlink(missing_ok=True)
                    removed += 1
                    continue
                try:
                    wait_for_rpc(f"http://127.0.0.1:{node_info.port}", timeout=2)
                except Exception:
                    try:
                        terminate_sui_node(node_info)
                    except Exception:
                        pass
                    path.unlink(missing_ok=True)
                    removed += 1
    return removed


def ensure_sui_pool_size() -> None:
    if SUI_POOL_SIZE <= 0 or SUI_ISOLATION_MODE != "private_pool":
        return

    ensure_pool_dirs()
    # Under lock: decide how many nodes we still need, without long-running create.
    with FileLock(pool_lock_path()):
        ready = [path for path in pool_ready_dir().iterdir() if path.is_file()]
        need = max(0, SUI_POOL_SIZE - len(ready))
    if need <= 0:
        return
    if not total_sui_process_budget_ok():
        return

    # Create one node outside the exclusive lock so acquires are not blocked.
    try:
        node_info = create_private_sui_node("__pool__")
    except Exception as exc:
        print(f"Sui pool create failed: {exc}", flush=True)
        return

    seed_data = parse_sui_seed(node_info)
    seed_data["pooled"] = True
    node_info.seed = json.dumps(seed_data)
    node_info.status = "ready"
    node_info.created_at = time.time()

    with FileLock(pool_lock_path()):
        ready = [path for path in pool_ready_dir().iterdir() if path.is_file()]
        if len(ready) < SUI_POOL_SIZE and total_sui_process_budget_ok():
            atomic_write_node_info(pool_ready_dir() / node_info.uuid, node_info)
        else:
            # Surplus (race/refill overshoot) — free the process immediately.
            terminate_sui_node(node_info)


def start_sui_pool_maintainer() -> None:
    global _POOL_THREAD_STARTED
    if SUI_POOL_SIZE <= 0 or SUI_ISOLATION_MODE != "private_pool":
        return

    with _POOL_THREAD_LOCK:
        if _POOL_THREAD_STARTED:
            return
        _POOL_THREAD_STARTED = True

    def maintain() -> None:
        while True:
            try:
                ensure_sui_pool_size()
            except Exception as exc:
                print(f"Sui pool refill failed: {exc}", flush=True)
            time.sleep(SUI_POOL_REFILL_INTERVAL)

    thread = threading.Thread(target=maintain, name="sui-pool-maintainer", daemon=True)
    thread.start()


def acquire_pooled_sui_node(team_id: str) -> NodeInfo | None:
    if SUI_POOL_SIZE <= 0 or SUI_ISOLATION_MODE != "private_pool":
        return None

    ensure_pool_dirs()
    with FileLock(pool_lock_path()):
        for path in sorted(pool_ready_dir().iterdir()):
            if not path.is_file():
                continue
            try:
                node_info = NodeInfo(**json.loads(path.read_text()))
            except Exception:
                path.unlink(missing_ok=True)
                continue
            leased_path = pool_leased_dir() / node_info.uuid
            try:
                path.replace(leased_path)
            except FileNotFoundError:
                continue
            try:
                wait_for_rpc(f"http://127.0.0.1:{node_info.port}", timeout=5)
                faucet_port = int(parse_sui_seed(node_info).get("faucet_port", 0) or 0)
                if faucet_port > 0:
                    wait_tcp("127.0.0.1", faucet_port, timeout=5)
            except Exception:
                try:
                    terminate_sui_node(node_info)
                except Exception:
                    pass
                leased_path.unlink(missing_ok=True)
                continue
            node_info.team = team_id
            seed = parse_sui_seed(node_info)
            seed["pooled"] = True
            seed["leased_at"] = time.time()
            node_info.seed = json.dumps(seed)
            node_info.status = "starting"
            atomic_write_node_info(leased_path, node_info)
            return node_info
    return None


def launch_sui_node(team_id: str) -> NodeInfo:
    pooled = acquire_pooled_sui_node(team_id)
    start_sui_pool_maintainer()
    if pooled is not None:
        # Kick refill immediately so the next team sees a warm node sooner.
        threading.Thread(target=ensure_sui_pool_size, name="sui-pool-kick", daemon=True).start()
        return pooled
    return create_private_sui_node(team_id)
