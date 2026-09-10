import json
import re
from pathlib import Path

from sandbox.launcher import generate_session_data
from sandbox.type import AccountInfo, NodeInfo

SANDBOX = Path(__file__).resolve().parents[1]
THIS_FILE = Path(__file__).name
FORBIDDEN = re.compile(r"COMPFEST|ROUTE_REGISTRY|INCENTIVE_VAULT|BLUECHIP|OPERATOR_ACCOUNT", re.I)


def test_no_challenge_identifiers_in_sandbox():
    offenders = []
    for path in SANDBOX.rglob("*"):
        if not path.is_file() or path.name == THIS_FILE:
            continue
        if "__pycache__" in path.parts or path.suffix == ".pyc":
            continue
        try:
            text = path.read_bytes().decode("utf-8")
        except UnicodeDecodeError:
            continue  # binary asset (fonts/images) under frontend/, not a source-leak vector
        for n, line in enumerate(text.splitlines(), 1):
            if FORBIDDEN.search(line):
                offenders.append(f"{path.relative_to(SANDBOX)}:{n}: {line.strip()}")
    assert not offenders, "challenge identifiers leaked into the launcher:\n" + "\n".join(offenders)


def _node_info(contract_data: dict) -> NodeInfo:
    return NodeInfo(
        port=9000,
        accounts=[
            AccountInfo(address="0xadmin", private_key="admin-priv", public_key="admin-pub"),
            AccountInfo(address="0xplayer", private_key="player-priv", public_key="player-pub"),
        ],
        pid=1234,
        uuid="test-uuid",
        team="test-team",
        contract_addr=json.dumps(contract_data),
    )


def _numbered_keys(payload: dict) -> list[str]:
    return sorted((k for k in payload if k not in ("0", "message")), key=int)


def _flatten(payload: dict) -> dict:
    merged = {}
    for k in _numbered_keys(payload):
        merged.update(payload[k])
    return merged


SAMPLE_CONTRACT_DATA = {
    "package_id": "0xpkg",
    "setup": "0xsetup",
    "objects": {"REGISTRY": "0xreg", "VAULT": "0xvault"},
}


def test_payload_numbered_keys_are_contiguous_single_entry_dicts():
    payload = generate_session_data(_node_info(SAMPLE_CONTRACT_DATA))
    keys = _numbered_keys(payload)
    assert keys == [str(i) for i in range(1, len(keys) + 1)]
    assert all(len(payload[k]) == 1 for k in keys)


def test_payload_key_order_is_privkey_wallet_package_setup_then_objects():
    payload = generate_session_data(_node_info(SAMPLE_CONTRACT_DATA))
    ordered_names = [next(iter(payload[k])) for k in _numbered_keys(payload)]
    assert ordered_names == ["PRIVKEY", "WALLET_ADDR", "PACKAGE_ID", "SETUP_ID", "REGISTRY", "VAULT"]


def test_setup_id_reads_the_setup_field():
    payload = generate_session_data(_node_info(SAMPLE_CONTRACT_DATA))
    assert _flatten(payload)["SETUP_ID"] == "0xsetup"


def test_author_object_keys_pass_through_verbatim():
    payload = generate_session_data(_node_info(SAMPLE_CONTRACT_DATA))
    values = _flatten(payload)
    assert values["REGISTRY"] == "0xreg"
    assert values["VAULT"] == "0xvault"


def test_arbitrary_unseen_author_key_passes_through():
    contract_data = {**SAMPLE_CONTRACT_DATA, "objects": {"WIDGET_ID": "0xwidget"}}
    payload = generate_session_data(_node_info(contract_data))
    assert _flatten(payload)["WIDGET_ID"] == "0xwidget"


def test_empty_objects_yields_only_base_keys_and_no_extras():
    contract_data = {**SAMPLE_CONTRACT_DATA, "objects": {}}
    payload = generate_session_data(_node_info(contract_data))
    assert _flatten(payload) == {
        "PRIVKEY": "player-priv",
        "WALLET_ADDR": "0xplayer",
        "PACKAGE_ID": "0xpkg",
        "SETUP_ID": "0xsetup",
    }
