"""Shared multi-tenant Sui node: one chain, one object set per team."""

from __future__ import annotations

import fcntl
import json
import logging
import os
import requests
import signal
import subprocess
import time
from pathlib import Path
from typing import Any
from uuid import uuid4

from .type import AccountInfo, NodeInfo
from .sui_helper import (
    SUI_GAS_BUDGET,
    call_function,
    create_sui_keypair,
    encode_contract_addr,
    find_object,
    fund_account,
    generate_sui_network_config,
    get_object_id_by_type,
    publish_package,
    run_checked,
    wait_for_rpc,
    wait_tcp,
    write_client_config,
)

logger = logging.getLogger("BlockchainGateway")

SUI_SHARED_ROOT = Path(os.getenv("SUI_SHARED_ROOT", "/tmp/sui-shared"))
SUI_SHARED_RPC_PORT = int(os.getenv("SUI_SHARED_RPC_PORT", "9000"))
SUI_SHARED_FAUCET_PORT = int(os.getenv("SUI_SHARED_FAUCET_PORT", "9123"))
SUI_CHALLENGE_PACKAGE = os.getenv("SUI_CHALLENGE_PACKAGE", "/home/ctf/setup")

# Transactions from one admin address serialise on its gas coin; this many
# admins can provision teams concurrently.
SUI_ADMIN_ACCOUNTS = int(os.getenv("SUI_ADMIN_ACCOUNTS", "4"))

# 10 SUI: players publish their own exploit package (the expensive part)
# plus a long tail of calls.
SUI_PLAYER_FUNDING_MIST = int(os.getenv("SUI_PLAYER_FUNDING_MIST", str(10 * 1_000_000_000)))

SHARED_RPC_URL = f"http://127.0.0.1:{SUI_SHARED_RPC_PORT}"
SHARED_FAUCET_URL = f"http://127.0.0.1:{SUI_SHARED_FAUCET_PORT}"

_STATE_FILE = SUI_SHARED_ROOT / "state.json"
_BOOTSTRAP_LOCK = SUI_SHARED_ROOT / "bootstrap.lock"
_NODE_PID_FILE = SUI_SHARED_ROOT / "node.pid"
_SHARED_NETWORK_DIR = SUI_SHARED_ROOT / "network"

_BOOTSTRAP_TIMEOUT = int(os.getenv("SUI_BOOTSTRAP_TIMEOUT", "300"))
_FULLNODE_CONFIG = _SHARED_NETWORK_DIR / "fullnode.yaml"

_WATCHDOG_INTERVAL = float(os.getenv("SUI_NODE_WATCHDOG_INTERVAL", "10"))


def _rpc_up() -> bool:
    """True when shared RPC answers; pid checks lie for zombies."""
    try:
        r = requests.post(
            SHARED_RPC_URL,
            json={"jsonrpc": "2.0", "id": 1, "method": "sui_getLatestCheckpointSequenceNumber", "params": []},
            timeout=2,
        )
        return r.ok and bool(r.json())
    except Exception:
        return False


def _bound_db_growth(config_path: Path) -> None:
    """Inject pruner settings into fullnode.yaml to cap DB growth."""
    if not config_path.exists():
        return
    text = config_path.read_text()
    if "db-pruner-period-secs:" in text:
        return
    pruner = (
        "\n"
        "pruner:\n"
        "  db-pruner-period-secs: 60\n"
        "  num-epochs-to-retain: 0\n"
        "  max-checkpoints-in-batch: 10\n"
        "  pruning-cron-period-secs: 60\n"
    )
    config_path.write_text(text + pruner)


def _start_shared_sui_node(*, regenesis: bool = False) -> int:
    """Start shared fullnode+faucet. Reuse network dir unless regenesis."""
    _SHARED_NETWORK_DIR.mkdir(parents=True, exist_ok=True)
    log_dir = SUI_SHARED_ROOT / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)

    if regenesis or not _FULLNODE_CONFIG.exists():
        generate_sui_network_config(_SHARED_NETWORK_DIR, SHARED_RPC_URL, SUI_SHARED_RPC_PORT)
    _bound_db_growth(_FULLNODE_CONFIG)

    sui_home = SUI_SHARED_ROOT / "home"
    sui_config_dir = sui_home / ".sui" / "sui_config"
    sui_config_dir.mkdir(parents=True, exist_ok=True)

    command = [
        "sui",
        "start",
        "--network.config",
        str(_SHARED_NETWORK_DIR),
        f"--with-faucet=0.0.0.0:{SUI_SHARED_FAUCET_PORT}",
        "--fullnode-rpc-port",
        str(SUI_SHARED_RPC_PORT),
    ]

    env = {
        **os.environ,
        "HOME": str(sui_home),
        "SUI_CONFIG_DIR": str(sui_config_dir),
    }

    with open(log_dir / "sui.stdout.log", "ab") as stdout, open(
        log_dir / "sui.stderr.log", "ab"
    ) as stderr:
        proc = subprocess.Popen(command, stdout=stdout, stderr=stderr, start_new_session=True, env=env)

    # Wait for RPC and faucet to be ready
    wait_for_rpc(SHARED_RPC_URL, timeout=120)
    wait_tcp("127.0.0.1", SUI_SHARED_FAUCET_PORT, timeout=120)

    # Record PID for liveness checks
    _NODE_PID_FILE.write_text(str(proc.pid))
    return proc.pid


_RESTART_LOCK = SUI_SHARED_ROOT / "node.restart.lock"


def _restart_shared_node() -> int:
    """Kill stale node, free ports, restart from existing network dir (flocked)."""
    with _Flock(_RESTART_LOCK):
        if _rpc_up():
            logger.info("shared node already recovered by another worker; skipping restart")
            try:
                return int(_NODE_PID_FILE.read_text().strip())
            except (FileNotFoundError, ValueError):
                return 0

        try:
            pid = int(_NODE_PID_FILE.read_text().strip())
        except (FileNotFoundError, ValueError):
            pid = 0
        if pid:
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            except PermissionError:
                pass
            try:
                os.waitpid(pid, os.WNOHANG)
            except (ChildProcessError, OSError):
                pass

        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if _rpc_up():
                break
            time.sleep(0.5)

        logger.warning("shared Sui node restarting after liveness loss (pid was %s)", pid or "?")
        return _start_shared_sui_node(regenesis=False)


class _Flock:
    def __init__(self, path: Path):
        self.path = path
        self._handle = None

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._handle = open(self.path, "a+")
        fcntl.flock(self._handle.fileno(), fcntl.LOCK_EX)
        return self

    def __exit__(self, *exc):
        try:
            fcntl.flock(self._handle.fileno(), fcntl.LOCK_UN)
        finally:
            self._handle.close()
            self._handle = None
        return False


def _read_state() -> dict[str, Any]:
    try:
        return json.loads(_STATE_FILE.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def _write_state(state: dict[str, Any]) -> None:
    _STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = _STATE_FILE.with_suffix(f".{os.getpid()}.tmp")
    tmp.write_text(json.dumps(state, indent=2))
    os.replace(tmp, _STATE_FILE)


def shared_node_pid() -> int:
    """Recorded on every tenant so the reaper's liveness check reflects the
    chain being up -- a stale pid here makes the reaper wipe every tenant.
    """
    try:
        return int(_NODE_PID_FILE.read_text().strip())
    except (FileNotFoundError, ValueError):
        return 0


def wait_for_shared_node(timeout: int = 180) -> None:
    wait_for_rpc(SHARED_RPC_URL, timeout=timeout)
    wait_tcp("127.0.0.1", SUI_SHARED_FAUCET_PORT, timeout=timeout)


def _admin_paths(index: int) -> tuple[str, str]:
    base = SUI_SHARED_ROOT / "admins" / f"admin-{index}"
    return str(base / "client.yaml"), str(base / "sui.keystore")


def _create_admin(index: int) -> dict[str, str]:
    key = create_sui_keypair(f"shared-admin-{index}")
    config_path, keystore_path = _admin_paths(index)
    Path(keystore_path).parent.mkdir(parents=True, exist_ok=True)
    Path(keystore_path).write_text(json.dumps([key["base64_key"]]))
    write_client_config(config_path, SHARED_RPC_URL, key["address"], keystore_path)

    for _ in range(5):
        try:
            fund_account(SHARED_FAUCET_URL, key["address"])
        except Exception as exc:
            logger.warning("admin %d faucet grant failed: %s", index, exc)
        time.sleep(0.2)

    return {"address": key["address"], "config": config_path}


def _find_cap_for(admin, package_id):
    cap = get_object_id_by_type(
        SHARED_RPC_URL, admin["address"], package_id, "setup", "AdminCap"
    )
    if not cap:
        raise RuntimeError(f"publish did not grant an AdminCap to {admin['address']}")
    return cap


def bootstrap() -> dict[str, Any]:
    state = _read_state()
    has_state = bool(state.get("package_id") and state.get("admins"))
    if has_state and _rpc_up():
        return state

    with _Flock(_BOOTSTRAP_LOCK):
        state = _read_state()
        has_state = bool(state.get("package_id") and state.get("admins"))
        if has_state and _rpc_up():
            return state

        if has_state and not _rpc_up():
            logger.warning("state.json present but shared node is down; restarting it")
            try:
                _restart_shared_node()
                wait_for_shared_node(timeout=_BOOTSTRAP_TIMEOUT)
                if _rpc_up():
                    logger.info("shared node recovered; reusing existing chain state")
                    return state
            except Exception as exc:
                logger.warning("shared node restart failed (%s); full rebootstrap", exc)
            state = {}
            has_state = False

        if not _rpc_up():
            logger.info("starting shared Sui node")
            _start_shared_sui_node()
            wait_for_shared_node(timeout=_BOOTSTRAP_TIMEOUT)

        logger.info("bootstrapping shared Sui node: %d admins", SUI_ADMIN_ACCOUNTS)
        admins = [_create_admin(i) for i in range(max(1, SUI_ADMIN_ACCOUNTS))]

        logger.info("publishing challenge package from %s", SUI_CHALLENGE_PACKAGE)
        for stale in ("Pub.local.toml", "Move.lock"):
            (Path(SUI_CHALLENGE_PACKAGE) / stale).unlink(missing_ok=True)
        package_id = publish_package(admins[0]["config"], SUI_CHALLENGE_PACKAGE)
        cap0 = _find_cap_for(admins[0], package_id)
        admins[0]["cap"] = cap0
        for admin in admins[1:]:
            result = call_function(
                admins[0]["config"], package_id, "setup", "mint_cap",
                args=[cap0, admin["address"]],
            )
            cap = find_object(result, package_id, "setup", "AdminCap", owned=True)
            if not cap:
                raise RuntimeError(f"mint_cap produced no AdminCap for {admin['address']}")
            admin["cap"] = cap
        logger.info("shared challenge package published: %s", package_id)

        state = {
            "package_id": package_id,
            "admins": admins,
            "rpc_url": SHARED_RPC_URL,
            "faucet_url": SHARED_FAUCET_URL,
            "rpc_port": SUI_SHARED_RPC_PORT,
        }
        _write_state(state)
        return state


def _pick_admin(state: dict[str, Any], key: str) -> tuple[int, dict[str, str]]:
    admins = state["admins"]
    index = hash(key) % len(admins)
    return index, admins[index]


def _gas_coin_ids(admin_config: str) -> list[str]:
    raw = run_checked(
        ["sui", "client", "--client.config", admin_config, "--json", "gas"],
        timeout=30,
    )
    data = json.loads(raw)
    rows = data if isinstance(data, list) else data.get("data", [])
    ids = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        coin_id = row.get("gasCoinId") or row.get("id") or row.get("objectId")
        if isinstance(coin_id, str) and coin_id.startswith("0x"):
            ids.append(coin_id)
    return ids


def _admin_pay(admin_config: str, recipient: str, amount_mist: int) -> None:
    # A transaction can be built against a coin version which has just been
    # consumed by another request (for example after a launcher worker was
    # restarted).  Sui explicitly confirms this rejection happened before
    # execution, so it is safe to fetch current coin references and rebuild.
    stale_object_error = "Transaction needs to be rebuilt because object"
    for attempt in range(3):
        coins = _gas_coin_ids(admin_config)
        if not coins:
            raise RuntimeError(f"admin {admin_config} has no gas coins to pay from")
        try:
            run_checked(
                [
                    "sui", "client", "--client.config", admin_config, "--json", "pay-sui",
                    "--input-coins", *coins[:8],
                    "--recipients", recipient,
                    "--amounts", str(amount_mist),
                    "--gas-budget", str(SUI_GAS_BUDGET),
                ],
                timeout=60,
            )
            return
        except RuntimeError as exc:
            if stale_object_error not in str(exc) or attempt == 2:
                raise
            logger.warning(
                "admin payment used a stale coin object; rebuilding transaction (%d/3)",
                attempt + 1,
            )
            time.sleep(0.2)


def _fund_player(admin_config: str, player_address: str) -> None:
    """The faucet is a single shared instance and every request reaches it
    from 127.0.0.1, so all teams share one rate-limit bucket; paying from an
    admin balance is not subject to that.
    """
    for _ in range(3):
        try:
            fund_account(SHARED_FAUCET_URL, player_address)
            return
        except Exception:
            time.sleep(0.5)

    logger.warning("faucet unavailable for %s, paying from admin", player_address)
    _admin_pay(admin_config, player_address, SUI_PLAYER_FUNDING_MIST)


def shared_bootstrap_state() -> dict[str, Any]:
    state = _read_state()
    return {"node_up": _rpc_up(), "package_id": state.get("package_id")}


def start_shared_node_watchdog() -> None:
    """Restart shared node from existing DB when RPC is down."""
    import threading

    def _watch():
        while True:
            try:
                if not _rpc_up() and _FULLNODE_CONFIG.exists():
                    logger.warning("watchdog: shared node RPC down, restarting")
                    _restart_shared_node()
            except Exception as exc:
                logger.warning("watchdog restart error: %s", exc)
            time.sleep(_WATCHDOG_INTERVAL)

    threading.Thread(target=_watch, daemon=True, name="sui-shared-watchdog").start()


def _handler_takes_package_id(deploy_handler) -> bool:
    import inspect

    try:
        return "package_id" in inspect.signature(deploy_handler).parameters
    except (TypeError, ValueError):
        return False


def create_shared_tenant(team_id: str, deploy_handler) -> NodeInfo:
    state = bootstrap()
    node_uuid = str(uuid4())
    admin_index, admin = _pick_admin(state, node_uuid)

    player_key = create_sui_keypair("player")
    player_config = str(SUI_SHARED_ROOT / "players" / node_uuid / "client.yaml")
    player_keystore = str(SUI_SHARED_ROOT / "players" / node_uuid / "sui.keystore")
    Path(player_keystore).parent.mkdir(parents=True, exist_ok=True)
    Path(player_keystore).write_text(json.dumps([player_key["base64_key"]]))
    write_client_config(player_config, SHARED_RPC_URL, player_key["address"], player_keystore)

    # Transactions from one address serialise on its gas coin, so hold this
    # admin's lock across both the funding transfer and the initialize call.
    # Different admins proceed in parallel.
    admin_lock = SUI_SHARED_ROOT / "admins" / f"admin-{admin_index}.lock"
    try:
        with _Flock(admin_lock):
            _fund_player(admin["config"], player_key["address"])
            args = (SHARED_RPC_URL, admin["config"], admin["address"], player_key["address"])
            if _handler_takes_package_id(deploy_handler):
                contract_addr = encode_contract_addr(deploy_handler(*args, package_id=state["package_id"]))
            else:
                logger.warning(
                    "deploy handler does not accept package_id; republishing per team"
                )
                contract_addr = encode_contract_addr(deploy_handler(*args))
    except Exception:
        import shutil

        shutil.rmtree(SUI_SHARED_ROOT / "players" / node_uuid, ignore_errors=True)
        raise

    seed_data = {
        "isolation_mode": "shared",
        "rpc_url": SHARED_RPC_URL,
        "faucet_url": SHARED_FAUCET_URL,
        "rpc_port": SUI_SHARED_RPC_PORT,
        "faucet_port": SUI_SHARED_FAUCET_PORT,
        "admin_config": admin["config"],
        "admin_index": admin_index,
        "player_config": player_config,
        "config_dir": str(SUI_SHARED_ROOT / "players" / node_uuid),
        "package_id": state["package_id"],
        "pid": shared_node_pid(),
    }

    return NodeInfo(
        port=str(SUI_SHARED_RPC_PORT),
        accounts=[
            AccountInfo(
                address=admin["address"],
                private_key="",  # deliberately blank; admin key is never handed to players
                public_key=admin["address"],
            ),
            AccountInfo(
                address=player_key["address"],
                private_key=player_key["bech32_key"],
                public_key=player_key["address"],
            ),
        ],
        pid=shared_node_pid(),
        uuid=node_uuid,
        team=team_id,
        seed=json.dumps(seed_data),
        contract_addr=contract_addr,
    )


def terminate_shared_tenant(node_info: NodeInfo) -> None:
    """On-chain objects are deliberately left in place: reclaiming them would
    mean extra transactions for no benefit, since a tenant that is gone from
    the instancer's state can no longer be reached through the proxy.
    """
    import shutil

    try:
        seed_data = json.loads(node_info.seed or "{}")
    except (TypeError, json.JSONDecodeError):
        seed_data = {}
    config_dir = seed_data.get("config_dir")
    if isinstance(config_dir, str) and config_dir:
        shutil.rmtree(config_dir, ignore_errors=True)


def start_bootstrap_thread() -> None:
    import threading

    def _run():
        try:
            bootstrap()
        except Exception as exc:
            logger.error("eager shared bootstrap failed (will retry on launch): %s", exc)

    threading.Thread(target=_run, daemon=True, name="sui-shared-bootstrap").start()
