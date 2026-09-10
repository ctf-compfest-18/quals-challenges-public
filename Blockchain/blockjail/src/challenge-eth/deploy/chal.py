import json
from pathlib import Path

import sandbox
from web3 import Web3


def set_balance(web3: Web3, account_address: str, amount: int) -> None:
    response = web3.provider.make_request("anvil_setBalance", [account_address, amount])
    if "error" in response or "result" not in response:
        raise RuntimeError(f"anvil_setBalance failed: {response}")


def deploy(
    web3: Web3,
    deployer_address: str,
    deployer_privateKey: str,
    player_address: str,
) -> str:
    artifact = json.loads(Path("compiled/Setup.sol/Setup.json").read_text())
    contract = web3.eth.contract(
        abi=artifact["abi"], bytecode=artifact["bytecode"]["object"]
    )
    transaction = contract.constructor().build_transaction(
        {
            "from": deployer_address,
            "nonce": web3.eth.get_transaction_count(deployer_address),
            "value": web3.to_wei(200, "ether"),
        }
    )
    signed = web3.eth.account.sign_transaction(transaction, deployer_privateKey)
    tx_hash = web3.eth.send_raw_transaction(signed.raw_transaction)
    receipt = web3.eth.wait_for_transaction_receipt(tx_hash)
    if receipt.status != 1 or not receipt.contractAddress:
        raise RuntimeError("Setup deployment failed")

    set_balance(web3, player_address, Web3.to_wei(11, "ether"))
    return receipt.contractAddress


app = sandbox.run_launcher(deploy)
