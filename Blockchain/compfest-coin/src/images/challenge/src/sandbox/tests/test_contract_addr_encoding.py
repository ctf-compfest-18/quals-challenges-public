import json

import pytest

from sandbox.launcher import generate_session_data
from sandbox.sui_helper import encode_contract_addr
from sandbox.type import AccountInfo, NodeInfo


def test_encode_contract_addr_dict_round_trips_through_json_loads():
    value = {"package_id": "0xpkg", "setup": "0xsetup", "objects": {"REGISTRY": "0xreg"}}
    assert json.loads(encode_contract_addr(value)) == value


def test_encode_contract_addr_str_passes_through_unchanged():
    raw = json.dumps({"package_id": "0xpkg"})
    assert encode_contract_addr(raw) == raw


def test_encode_contract_addr_rejects_other_types():
    with pytest.raises(TypeError):
        encode_contract_addr(1234)


def _node_info_from_deploy_return(deploy_return: dict) -> NodeInfo:
    """Builds a NodeInfo the way blockchain_manager.py / sui_shared.py do:
    through encode_contract_addr, never by assigning the dict directly."""
    return NodeInfo(
        port=9000,
        accounts=[
            AccountInfo(address="0xadmin", private_key="admin-priv", public_key="admin-pub"),
            AccountInfo(address="0xplayer", private_key="player-priv", public_key="player-pub"),
        ],
        pid=1234,
        uuid="test-uuid",
        team="test-team",
        contract_addr=encode_contract_addr(deploy_return),
    )


def test_deploy_dict_survives_the_real_pipeline_into_session_data():
    """Seam test for the dict-vs-JSON-string break.

    chal.py::deploy returns a plain dict (the documented author contract).
    generate_session_data does json.loads(node_info.contract_addr), so
    anything between deploy() and NodeInfo that skips encode_contract_addr
    breaks /launch and /flag identically -- this composes that real path
    instead of hand-building contract_addr with json.dumps like
    test_session_payload.py does, so it fails if the serialization step is
    ever reverted.
    """
    deploy_return = {
        "package_id": "0xpkg",
        "setup": "0xsetup",
        "objects": {"REGISTRY": "0xreg"},
    }

    node_info = _node_info_from_deploy_return(deploy_return)
    payload = generate_session_data(node_info)

    flattened = {}
    for key, entry in payload.items():
        if key in ("0", "message"):
            continue
        flattened.update(entry)

    assert flattened["PACKAGE_ID"] == "0xpkg"
    assert flattened["SETUP_ID"] == "0xsetup"
