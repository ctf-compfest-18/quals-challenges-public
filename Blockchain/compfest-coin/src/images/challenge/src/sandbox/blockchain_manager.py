import os
import re
import shlex
import signal
import sys
import json
import time
import random
import asyncio
import subprocess
import threading
from threading import Thread
from typing import Any, Callable, Literal
from uuid import uuid4

# Third-party imports
from base58 import b58encode
from web3 import Web3
from eth_account import Account as EthAccount
from eth_account.hdaccount import generate_mnemonic
from solders.keypair import Keypair # type: ignore
from starknet_py.contract import Contract as CairoContract
from starknet_py.net.account.account import Account as CairoAccount, KeyPair as CairoKeyPair
from starknet_py.net.full_node_client import FullNodeClient as CairoFullNodeClient
from solana.rpc.async_api import AsyncClient as SolanaClient

from sandbox.helper import FileLock, PersistentStore
from . import metrics as ctf_metrics
from .lifecycle import is_expired, pid_alive, stamp_lease
from .solana_helper import is_solved as solana_is_solved
from .sui_helper import check_solved as sui_check_solved
from .sui_helper import encode_contract_addr
from .sui_helper import fund_account as sui_fund_account
from .sui_helper import launch_sui_node as sui_launch_node
from .sui_helper import parse_sui_seed
from .sui_helper import reap_pool_orphans
from .sui_helper import start_sui_pool_maintainer
from .sui_helper import terminate_sui_node
from .sui_helper import SUI_ISOLATION_MODE, SUI_POOL_SIZE
from .sui_shared import (
    create_shared_tenant,
    start_bootstrap_thread,
    start_shared_node_watchdog,
    terminate_shared_tenant,
)

SUI_SHARED = SUI_ISOLATION_MODE == "shared"

EthAccount.enable_unaudited_hdwallet_features()

# Local imports
from .type import AccountInfo, NodeInfo

# Environment configuration
BLOCKCHAIN_TYPE = os.getenv("BLOCKCHAIN_TYPE")
print("Starting Blockchain manager...")
if not BLOCKCHAIN_TYPE:
    print("BLOCKCHAIN_TYPE environment variable not defined")
    sys.exit(1)

# Directory setup
INSTANCE_BY_TEAM_DIR = "/tmp/instances-by-team"
INSTANCE_BY_UUID_DIR = "/tmp/instances-by-uuid"
INSTANCE_RESERVED_DIR = "/tmp/instances-reserved"
RESERVATION_LOCK_FILE = "/tmp/instance-reservation.lock"
# A reservation older than this belonged to a launch that died mid-flight.
RESERVATION_STALE_SECONDS = float(os.getenv("RESERVATION_STALE_SECONDS", "600"))
PICKLE_STATE_FILE = "/tmp/solana_state.pickle"
LOCK_FILE = "/tmp/solana.lock"
INSTANCE_TTL_SECONDS = int(os.getenv("INSTANCE_TTL_SECONDS", os.getenv("SUI_INSTANCE_TTL_SECONDS", "1800")))
SUI_MAX_ACTIVE = int(os.getenv("SUI_MAX_ACTIVE", "0"))
REAPER_INTERVAL_SECONDS = float(os.getenv("REAPER_INTERVAL_SECONDS", "5"))
os.makedirs(INSTANCE_BY_TEAM_DIR, exist_ok=True)
os.makedirs(INSTANCE_BY_UUID_DIR, exist_ok=True)
os.makedirs(INSTANCE_RESERVED_DIR, exist_ok=True)


class InstanceCapacityError(RuntimeError):
    def __init__(self, active: int, max_active: int):
        self.active = active
        self.max_active = max_active
        super().__init__(f"Sui instance capacity reached ({active}/{max_active})")

EVM_VERSION = os.getenv("EVM_VERSION") or None
ANVIL_EXTRA_OPTIONS = shlex.split(os.getenv("ANVIL_EXTRA_OPTIONS") or "")

if BLOCKCHAIN_TYPE == "eth":
    print("EVM_VERSION:", EVM_VERSION)

def get_solana_state():
    store = PersistentStore(PICKLE_STATE_FILE)
    state = store.get('solana_state')
    if state:
        return state['validator_pid'], state['system_keypair']
    return None, None

def save_solana_state(pid, keypair):
    store = PersistentStore(PICKLE_STATE_FILE)
    store.set('solana_state', {
        'validator_pid': pid,
        'system_keypair': keypair
    })

async def initialize_solana_validator():
    global VALIDATOR_PROCESS_ID, SYSTEM_KEYPAIR
    
    with FileLock(LOCK_FILE):
        # Check if another process has already initialized
        pid, keypair = get_solana_state()
        if pid is not None:
            VALIDATOR_PROCESS_ID = pid
            SYSTEM_KEYPAIR = keypair
            return

        # Initialize new validator
        VALIDATOR_PROCESS_ID = None
        SYSTEM_KEYPAIR = Keypair()
        
        node_port = 3001
        node_uuid = str(uuid4())
        
        print("Starting Solana validator...")
        VALIDATOR_PROCESS_ID = (await asyncio.create_subprocess_exec(
            "solana-test-validator", "--rpc-port", str(node_port), "--ledger", node_uuid,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )).pid
        
        # Save state immediately after getting PID
        save_solana_state(VALIDATOR_PROCESS_ID, SYSTEM_KEYPAIR)

        # Wait for validator to be ready
        while True:
            try:
                subprocess.run(
                    ["solana", "cluster-version", "--url", f"http://0.0.0.0:{node_port}"],
                    check=True,
                    capture_output=True,
                    timeout=5
                )
                print("Solana validator is ready!")
                break
            except subprocess.TimeoutExpired:
                continue
            except subprocess.CalledProcessError:
                await asyncio.sleep(0.5)

# Initialize Solana validator if using Solana
if BLOCKCHAIN_TYPE == "solana":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(initialize_solana_validator())

if BLOCKCHAIN_TYPE != "solana":
    VALIDATOR_PROCESS_ID = None
    SYSTEM_KEYPAIR = None

# Helper functions
def instance_exists(uuid: str) -> bool:
    return os.path.exists(f"{INSTANCE_BY_UUID_DIR}/{uuid}")

def team_instance_exists(team_id: str) -> bool:
    return os.path.exists(f"{INSTANCE_BY_TEAM_DIR}/{team_id}")

def load_instance(uuid: str) -> NodeInfo:
    with open(f"{INSTANCE_BY_UUID_DIR}/{uuid}", "r") as file:
        return NodeInfo(**json.load(file))

def load_team_instance(team_id: str) -> NodeInfo:
    with open(f"{INSTANCE_BY_TEAM_DIR}/{team_id}", "r") as file:
        return NodeInfo(**json.load(file))

def remove_instance_data(node_info: NodeInfo):
    for path in (f"{INSTANCE_BY_UUID_DIR}/{node_info.uuid}", f"{INSTANCE_BY_TEAM_DIR}/{node_info.team}"):
        try:
            os.remove(path)
        except FileNotFoundError:
            pass

def save_instance_data(node_info: NodeInfo):
    def atomic_write(path: str):
        tmp = f"{path}.{os.getpid()}.{id(node_info)}.tmp"
        with open(tmp, "w") as file:
            json.dump(node_info.to_dict(), file)
        os.replace(tmp, path)

    atomic_write(f"{INSTANCE_BY_UUID_DIR}/{node_info.uuid}")
    atomic_write(f"{INSTANCE_BY_TEAM_DIR}/{node_info.team}")

def active_instance_count() -> int:
    try:
        return sum(1 for name in os.listdir(INSTANCE_BY_UUID_DIR) if len(name) == 36 and name.count("-") == 4)
    except FileNotFoundError:
        return 0


def normalize_sui_id(value: str) -> str:
    """Canonical 64-hex form of a Sui object ID or address.

    Sui accepts equivalent short and zero-padded spellings of the same ID, so
    comparing the raw strings would let `0x2` slip past a filter holding
    `0x0000...02`.
    """
    body = value[2:] if value[:2].lower() == "0x" else value
    return body.lower().lstrip("0").rjust(64, "0")


def _extract_ids(obj: Any, ids: set[str]) -> None:
    """Collect 0x-hex strings from nested dicts/lists."""
    if isinstance(obj, str) and obj.startswith("0x"):
        ids.add(normalize_sui_id(obj))
    elif isinstance(obj, dict):
        for v in obj.values():
            _extract_ids(v, ids)
    elif isinstance(obj, list):
        for v in obj:
            _extract_ids(v, ids)


def _tenant_object_index() -> dict[str, set[str]]:
    """Map instance uuid -> provisioned object IDs and addresses (no mtime cache)."""
    by_uuid: dict[str, set[str]] = {}
    try:
        names = os.listdir(INSTANCE_BY_UUID_DIR)
    except FileNotFoundError:
        names = []

    for name in names:
        if not (len(name) == 36 and name.count("-") == 4):
            continue
        try:
            node = load_instance(name)
        except Exception:
            continue

        ids: set[str] = set()
        try:
            contract = json.loads(node.contract_addr or "{}")
        except (TypeError, json.JSONDecodeError):
            contract = {}
        if isinstance(contract, dict):
            _extract_ids(contract, ids)
        for account in node.accounts or []:
            if getattr(account, "address", None):
                ids.add(normalize_sui_id(account.address))
        by_uuid[name] = ids

    return by_uuid


def foreign_object_ids(uuid: str) -> set[str]:
    """Object IDs belonging to some other live tenant.

    Computed as a set difference against this tenant's own IDs, which is what
    makes it safe in shared mode: the package ID and the admin addresses appear
    in every tenant's set, so they cancel out and are never treated as foreign.
    """
    uuid = str(uuid)
    index = _tenant_object_index()
    own = index.get(uuid, set())
    foreign: set[str] = set()
    for other_uuid, ids in index.items():
        if other_uuid != uuid:
            foreign |= ids
    return foreign - own


def reserved_slot_count() -> int:
    """Count in-flight launches, dropping reservations from dead workers."""
    now = time.time()
    count = 0
    try:
        names = os.listdir(INSTANCE_RESERVED_DIR)
    except FileNotFoundError:
        return 0
    for name in names:
        path = f"{INSTANCE_RESERVED_DIR}/{name}"
        try:
            if now - os.path.getmtime(path) > RESERVATION_STALE_SECONDS:
                os.remove(path)
                continue
        except FileNotFoundError:
            continue
        count += 1
    return count


def reserve_slot(team_id: str) -> None:
    """Claim capacity before the slow part of a launch.

    A Sui launch takes seconds (shared) to minutes (private pool) before it
    writes its instance file, and active_instance_count() only sees that file.
    Without a reservation every concurrent launch reads the same pre-launch
    count and sails past max_active together -- which on the private-pool path
    means several 1.5GB nodes and an OOM kill.
    """
    with FileLock(RESERVATION_LOCK_FILE):
        if team_instance_exists(team_id) or os.path.exists(f"{INSTANCE_RESERVED_DIR}/{team_id}"):
            raise RuntimeError("Instance already exists for this team")
        if SUI_MAX_ACTIVE > 0:
            active = active_instance_count() + reserved_slot_count()
            if active >= SUI_MAX_ACTIVE:
                ctf_metrics.inc("capacity_reject")
                raise InstanceCapacityError(active, SUI_MAX_ACTIVE)
        with open(f"{INSTANCE_RESERVED_DIR}/{team_id}", "w") as handle:
            handle.write(str(time.time()))


def release_slot(team_id: str) -> None:
    try:
        os.remove(f"{INSTANCE_RESERVED_DIR}/{team_id}")
    except FileNotFoundError:
        pass


def stamp_lease_with_default_ttl(node_info: NodeInfo, ttl_seconds: int | None = None) -> NodeInfo:
    ttl = INSTANCE_TTL_SECONDS if ttl_seconds is None else ttl_seconds
    return stamp_lease(node_info, ttl)


_REAPER_STARTED = False
_REAPER_LOCK = threading.Lock()


def reap_once() -> dict:
    """Scan UUID instances; expire or reap dead Sui nodes."""
    stats = {"expired": 0, "dead": 0, "scanned": 0}
    try:
        names = os.listdir(INSTANCE_BY_UUID_DIR)
    except FileNotFoundError:
        return stats

    for name in names:
        if not (len(name) == 36 and name.count("-") == 4):
            continue
        stats["scanned"] += 1
        try:
            node = load_instance(name)
        except Exception:
            continue

        if BLOCKCHAIN_TYPE == "sui":
            seed = parse_sui_seed(node)
            try:
                pid = int(seed.get("pid") or node.pid or 0)
            except (TypeError, ValueError):
                pid = int(node.pid or 0)
        else:
            try:
                pid = int(node.pid or 0)
            except (TypeError, ValueError):
                pid = 0

        if is_expired(node):
            terminate_node_process(node)
            stats["expired"] += 1
            ctf_metrics.inc("reap_expired")
            continue

        # Shared mode: stamped pid is the shared node; restart makes it stale for
        # every tenant — only TTL/kill reaps. Node liveness is the watchdog.
        if SUI_SHARED:
            continue

        if node.team != "__pool__" and not pid_alive(pid):
            if BLOCKCHAIN_TYPE == "sui":
                try:
                    terminate_sui_node(node)
                except Exception as exc:
                    print(f"reaper dead-node cleanup error: {exc}", flush=True)
            remove_instance_data(node)
            stats["dead"] += 1
            ctf_metrics.inc("reap_dead")

    if BLOCKCHAIN_TYPE == "sui" and not SUI_SHARED:
        try:
            stats["pool_orphans"] = reap_pool_orphans()
        except Exception as exc:
            print(f"pool orphan reaper error: {exc}", flush=True)
            stats["pool_orphans"] = 0
    return stats


def start_instance_reaper(interval: float | None = None) -> None:
    global _REAPER_STARTED
    interval = REAPER_INTERVAL_SECONDS if interval is None else interval
    with _REAPER_LOCK:
        if _REAPER_STARTED:
            return
        _REAPER_STARTED = True

    def loop():
        while True:
            try:
                reap_once()
            except Exception as exc:
                print(f"reaper error: {exc}", flush=True)
            time.sleep(interval)

    Thread(target=loop, name="instance-reaper", daemon=True).start()


# Node management functions
def terminate_node_process(node_info: NodeInfo):
    print(f"Terminating node {node_info.team} {node_info.uuid}")
    try:
        node_info.status = "terminating"
        if BLOCKCHAIN_TYPE == "sui":
            # Shared mode has no per-team process; killing one would take the
            # whole chain down with every other team on it.
            if SUI_SHARED:
                terminate_shared_tenant(node_info)
            else:
                terminate_sui_node(node_info)
        elif BLOCKCHAIN_TYPE != "solana":
            os.kill(node_info.pid, signal.SIGTERM)
    finally:
        remove_instance_data(node_info)

def schedule_node_termination(node_info: NodeInfo):
    """TTL ownership.

    Sui: durable expires_at + reaper is the source of truth (survives worker restart).
    Optional backup timer only when SUI_TIMER_TTL_BACKUP=true.
    Other chains: keep legacy sleep-once daemon thread.
    """
    if BLOCKCHAIN_TYPE == "sui" and os.getenv("SUI_TIMER_TTL_BACKUP", "false").lower() not in {
        "1", "true", "yes",
    }:
        return None

    def termination_task():
        delay = float(INSTANCE_TTL_SECONDS)
        expires_at = getattr(node_info, "expires_at", None)
        if expires_at is not None:
            delay = max(0.0, float(expires_at) - time.time())
        time.sleep(delay)
        if instance_exists(node_info.uuid):
            terminate_node_process(node_info)
            ctf_metrics.inc("kill_total")

    termination_thread = Thread(target=termination_task, daemon=True)
    termination_thread.start()
    return termination_thread

# Blockchain-specific node launchers
async def launch_cairo_node(team_id: str) -> NodeInfo | None:
    if not team_id:
        return None

    node_port = random.randint(30000, 60000)
    node_uuid = str(uuid4())
    seed_message = "Seed to replicate this account sequence: "
    account_pattern = re.compile(
        r"Account address.*?(0x[a-f0-9]+).*?Private key.*?(0x[a-f0-9]+).*?Public key.*?(0x[a-f0-9]+)",
        flags=re.DOTALL
    )

    # Start Starknet devnet process
    devnet_process = await asyncio.create_subprocess_exec(
        "starknet-devnet",
        f"--port={node_port}",
        "--accounts=2",
        stdout=asyncio.subprocess.PIPE,
    )

    # Wait for node initialization
    client = CairoFullNodeClient(f"http://127.0.0.1:{node_port}")
    output = await devnet_process.stdout.readline()
    while seed_message.encode() not in output:
        output += b"\n" + await devnet_process.stdout.readline()

    # Verify node readiness
    while True:
        try:
            await client.get_block()
            break
        except Exception:
            await asyncio.sleep(0.1)

    # Extract account information
    account_matches = account_pattern.findall(output.decode())
    node_accounts = [
        AccountInfo(
            address=match[0],
            private_key=match[1],
            public_key=match[2]
        ) for match in account_matches
    ]

    # Create node information
    seed_match = re.search(f"{seed_message}(.*)$", output.decode())
    node_info = NodeInfo(
        port=node_port,
        accounts=node_accounts,
        pid=devnet_process.pid,
        uuid=node_uuid,
        team=team_id,
        seed=seed_match.group(1) if seed_match else None
    )

    schedule_node_termination(node_info)
    return node_info

def launch_ethereum_node(team_id: str) -> NodeInfo:
    node_port = random.randint(30000, 60000)
    mnemonic = generate_mnemonic(12, "english")
    node_uuid = str(uuid4())

    
    # Setup anvil
    anvil_args = [
        "anvil",
        "--accounts", "2",
        "--balance", "5000",
        "--mnemonic", mnemonic,
        "--port", str(node_port),
        "--block-base-fee-per-gas", "0"
    ]
    if EVM_VERSION is not None:
        anvil_args.append("--hardfork")
        anvil_args.append(EVM_VERSION)
    anvil_args.extend(ANVIL_EXTRA_OPTIONS)
    
    # Start Anvil process
    anvil_process = subprocess.Popen(
        args=anvil_args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )

    # Wait for node initialization
    web3 = Web3(Web3.HTTPProvider(f"http://127.0.0.1:{node_port}"))
    while True:
        if anvil_process.poll() is not None:
            errno = str(anvil_process.returncode)
            stdout, stderr = anvil_process.communicate()
            raise RuntimeError(f"Anvil process failed to start (errno: {errno}). "
                               f"stdout: {stdout.decode()}. stderr: {stderr.decode()}.")
        if web3.is_connected():
            break
        time.sleep(0.1)

    # Generate accounts
    deployer_account = EthAccount.from_mnemonic(mnemonic, account_path="m/44'/60'/0'/0/0")
    player_account = EthAccount.from_mnemonic(mnemonic, account_path="m/44'/60'/0'/0/1")

    node_accounts = [
        AccountInfo(
            address=deployer_account.address,
            private_key=deployer_account.key.hex(),
            public_key=deployer_account.address
        ),
        AccountInfo(
            address=player_account.address,
            private_key=player_account.key.hex(),
            public_key=player_account.address
        )
    ]

    node_info = NodeInfo(
        port=node_port,
        accounts=node_accounts,
        pid=anvil_process.pid,
        uuid=node_uuid,
        team=team_id,
        seed=mnemonic
    )

    schedule_node_termination(node_info)
    return node_info

# for solana we can only deploy one node because it's complicated to setup the faucet port
async def launch_solana_node(team_id: str) -> NodeInfo:
    node_port = 3001
    node_uuid = str(uuid4())

    # Generate keypairs
    system_keypair = SYSTEM_KEYPAIR
    player_keypair = Keypair()
    context_keypair = Keypair()

    node_accounts = [
        AccountInfo(
            address=str(system_keypair.pubkey()),
            private_key=b58encode(bytes(system_keypair)).decode(),
            public_key=str(system_keypair.pubkey())
        ),
        AccountInfo(
            address=str(player_keypair.pubkey()),
            private_key=b58encode(bytes(player_keypair)).decode(),
            public_key=str(player_keypair.pubkey())
        ),
        AccountInfo(
            address=str(context_keypair.pubkey()),
            private_key=b58encode(bytes(context_keypair)).decode(),
            public_key=str(context_keypair.pubkey())
        ),
    ]

    node_info = NodeInfo(
        port=node_port,
        accounts=node_accounts,
        pid=VALIDATOR_PROCESS_ID,
        uuid=node_uuid,
        team=team_id,
        seed=None,
        contract_addr=None
    )

    schedule_node_termination(node_info)
    return node_info

def launch_sui_node(team_id: str) -> NodeInfo:
    # Do not schedule TTL until deploy succeeds and lease is stamped.
    return sui_launch_node(team_id)

# Main blockchain manager class
class BlockchainManager:
    def __init__(self, blockchain_type: Literal["cairo", "eth", "solana", "sui"]):
        self.blockchain_type = blockchain_type
        self.client = self._initialize_client()
        if self.blockchain_type == "sui":
            print(
                f"Sui instancer config: mode={SUI_ISOLATION_MODE} pool={SUI_POOL_SIZE} "
                f"max_active={SUI_MAX_ACTIVE} ttl={INSTANCE_TTL_SECONDS}",
                flush=True,
            )
            if SUI_SHARED:
                start_bootstrap_thread()
                start_shared_node_watchdog()
            else:
                start_sui_pool_maintainer()
            start_instance_reaper()

    def _initialize_client(self):
        if self.blockchain_type == "cairo":
            return CairoFullNodeClient("http://127.0.0.1:8545")
        elif self.blockchain_type == "eth":
            return Web3(Web3.HTTPProvider("http://127.0.0.1:8545"))
        elif self.blockchain_type == "sui":
            return None
        return None

    async def start_instance(
        self,
        team_id: str,
        deploy_handler: Callable[
            [CairoFullNodeClient, CairoAccount, CairoAccount], str
        ] | Callable[[Web3, str, str, str], str]
        | Callable[[SolanaClient, Keypair, Keypair, Keypair], str]
    ) -> NodeInfo:
        if self.blockchain_type == "sui":
            reserve_slot(team_id)
        elif team_instance_exists(team_id):
            raise RuntimeError("Instance already exists for this team")

        try:
            node_info = None
            if self.blockchain_type == "cairo":
                node_info = await self._start_cairo_instance(team_id, deploy_handler)
            elif self.blockchain_type == "eth":
                node_info = await self._start_ethereum_instance(team_id, deploy_handler)
            elif self.blockchain_type == "solana":
                node_info = await self._start_solana_instance(team_id, deploy_handler)
            elif self.blockchain_type == "sui":
                node_info = self._start_sui_instance(team_id, deploy_handler)

            if not node_info:
                raise RuntimeError("Failed to create blockchain instance")

            save_instance_data(node_info)
            return node_info
        finally:
            # Held only until the real instance file exists (or the launch fails).
            if self.blockchain_type == "sui":
                release_slot(team_id)

    async def _start_cairo_instance(self, team_id: str, deploy_handler):
        node_info = await launch_cairo_node(team_id)
        if not node_info:
            return None

        client = CairoFullNodeClient(f"http://127.0.0.1:{node_info.port}")
        system_account = await self._create_cairo_account(client, node_info.accounts[1])
        player_account = await self._create_cairo_account(client, node_info.accounts[0])
        
        contract_address = hex(await deploy_handler(client, system_account, player_account))
        node_info.contract_addr = contract_address
        return node_info

    async def _create_cairo_account(self, client, account_info):
        return CairoAccount(
            client=client,
            address=account_info.address,
            key_pair=CairoKeyPair.from_private_key(account_info.private_key),
            chain=await client.get_chain_id()
        )

    async def _start_ethereum_instance(self, team_id: str, deploy_handler):
        node_info = launch_ethereum_node(team_id)
        web3 = Web3(Web3.HTTPProvider(f"http://127.0.0.1:{node_info.port}"))
        
        deployer_account = EthAccount.from_mnemonic(
            node_info.seed, account_path="m/44'/60'/0'/0/0"
        )
        contract_address = deploy_handler(
            web3,
            deployer_account.address,
            deployer_account.key.hex(),
            node_info.accounts[1].address
        )
        if asyncio.iscoroutine(deploy_handler):
            contract_address = await contract_address
        
        node_info.contract_addr = contract_address
        return node_info

    async def _start_solana_instance(self, team_id: str, deploy_handler):
        node_info = await launch_solana_node(team_id)
        client = SolanaClient(f"http://0.0.0.0:{node_info.port}")
        
        system_keypair = Keypair.from_base58_string(node_info.accounts[0].private_key)
        player_keypair = Keypair.from_base58_string(node_info.accounts[1].private_key)
        context_keypair = Keypair.from_base58_string(node_info.accounts[2].private_key)
        
        contract_address = await deploy_handler(client, system_keypair, player_keypair, context_keypair)
        node_info.contract_addr = contract_address
        return node_info

    def terminate_instance(self, team_id: str):
        if not team_instance_exists(team_id):
            raise RuntimeError("No instance exists for this team")
        terminate_node_process(load_team_instance(team_id))
        ctf_metrics.inc("kill_total")

    async def verify_solution(self, team_id: str) -> bool:
        if not team_instance_exists(team_id):
            raise RuntimeError("Instance not found for this team")

        node_info = load_team_instance(team_id)
        if self.blockchain_type == "cairo":
            return await self._check_cairo_solution(node_info)
        elif self.blockchain_type == "eth":
            return self._check_ethereum_solution(node_info)
        elif self.blockchain_type == "solana":
            return await self._check_solana_solution(node_info)
        elif self.blockchain_type == "sui":
            return self._check_sui_solution(node_info)
        return False

    async def _check_cairo_solution(self, node_info: NodeInfo) -> bool:
        client = CairoFullNodeClient(f"http://127.0.0.1:{node_info.port}")
        system_account = await self._create_cairo_account(client, node_info.accounts[1])
        
        contract = await CairoContract.from_address(
            int(node_info.contract_addr, 16), system_account
        )
        result = await contract.functions.get("is_solved").call()
        return result[0] if isinstance(result, (tuple, list)) else result

    def _check_ethereum_solution(self, node_info: NodeInfo) -> bool:
        web3 = Web3(Web3.HTTPProvider(f"http://127.0.0.1:{node_info.port}"))
        call_data = web3.eth.call({
            "to": node_info.contract_addr,
            "data": Web3.keccak(text="isSolved()")[:4],
        })
        return int(call_data.hex(), 16) == 1

    async def _check_solana_solution(self, node_info: NodeInfo) -> bool:
        client = SolanaClient(f"http://0.0.0.0:{node_info.port}")
        system_keypair = Keypair.from_base58_string(node_info.accounts[0].private_key)
        context_keypair = Keypair.from_base58_string(node_info.accounts[2].private_key)
        # program_id = Pubkey(node_info.contract_addr)
        is_solved = await solana_is_solved(client, system_keypair, context_keypair)
        return is_solved

    def _start_sui_instance(self, team_id: str, deploy_handler):
        if SUI_SHARED:
            return self._start_shared_sui_instance(team_id, deploy_handler)

        node_info = launch_sui_node(team_id)
        node_info.status = "starting"
        try:
            seed_data = parse_sui_seed(node_info)
            rpc_url = seed_data["rpc_url"]
            faucet_url = seed_data["faucet_url"]
            admin_config = seed_data["admin_config"]
            admin_address = node_info.accounts[0].address
            player_address = node_info.accounts[1].address

            contract_addr = deploy_handler(rpc_url, admin_config, admin_address, player_address)
            node_info.contract_addr = encode_contract_addr(contract_addr)

            for _ in range(30):
                try:
                    sui_fund_account(faucet_url, player_address)
                    break
                except Exception:
                    time.sleep(1)

            stamp_lease_with_default_ttl(node_info)
            node_info.status = "ready"
            schedule_node_termination(node_info)
            return node_info
        except Exception:
            # Failed deploy must not leak a running node or pool lease.
            try:
                terminate_sui_node(node_info)
            except Exception as cleanup_exc:
                print(f"failed-deploy cleanup error: {cleanup_exc}", flush=True)
            raise

    def _start_shared_sui_instance(self, team_id: str, deploy_handler):
        """Provision a tenant on the shared node.

        No node to launch and nothing to wait for -- create_shared_tenant funds
        the player and runs challenge::initialize, and returns with the object
        set already recorded on contract_addr.
        """
        node_info = create_shared_tenant(team_id, deploy_handler)
        stamp_lease_with_default_ttl(node_info)
        node_info.status = "ready"
        schedule_node_termination(node_info)
        return node_info

    def _check_sui_solution(self, node_info: NodeInfo) -> bool:
        seed_data = parse_sui_seed(node_info)
        rpc_url = seed_data["rpc_url"]
        try:
            contract_data = json.loads(node_info.contract_addr)
            setup_id = contract_data.get("setup")
        except (json.JSONDecodeError, TypeError, AttributeError):
            return False
        if not setup_id:
            return False
        return sui_check_solved(rpc_url, setup_id)

# Global instance initialization
BLOCKCHAIN_MANAGER = BlockchainManager(BLOCKCHAIN_TYPE)
