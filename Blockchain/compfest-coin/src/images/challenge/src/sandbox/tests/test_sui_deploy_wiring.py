import json

import sandbox.blockchain_manager as blockchain_manager
import sandbox.sui_shared as sui_shared
from sandbox.type import AccountInfo, NodeInfo

DEPLOY_RETURN = {"package_id": "0xpkg", "setup": "0xsetup", "objects": {"REGISTRY": "0xreg"}}


def _fake_deploy_handler(rpc_url, admin_config, admin_address, player_address, package_id=None):
    return dict(DEPLOY_RETURN)


def test_start_sui_instance_wires_encode_contract_addr_at_the_call_site(monkeypatch):
    """Drives the real BlockchainManager._start_sui_instance, not a hand-composed
    stand-in. Fails if the encode_contract_addr call at that call site is ever
    removed or reverted to a bare assignment."""

    def fake_launch_sui_node(team_id):
        return NodeInfo(
            port="9000",
            accounts=[
                AccountInfo(address="0xadmin", private_key="admin-priv", public_key="0xadmin"),
                AccountInfo(address="0xplayer", private_key="player-priv", public_key="0xplayer"),
            ],
            pid=4321,
            uuid="fake-uuid",
            team=team_id,
            seed=json.dumps({
                "rpc_url": "http://127.0.0.1:9000",
                "faucet_url": "http://127.0.0.1:9123",
                "admin_config": "/fake/admin/client.yaml",
            }),
        )

    monkeypatch.setattr(blockchain_manager, "SUI_SHARED", False)
    monkeypatch.setattr(blockchain_manager, "launch_sui_node", fake_launch_sui_node)
    monkeypatch.setattr(blockchain_manager, "sui_fund_account", lambda *a, **k: None)
    monkeypatch.setattr(blockchain_manager, "schedule_node_termination", lambda *a, **k: None)
    monkeypatch.setattr(blockchain_manager, "terminate_sui_node", lambda *a, **k: None)

    node_info = blockchain_manager.BLOCKCHAIN_MANAGER._start_sui_instance("test-team", _fake_deploy_handler)

    assert isinstance(node_info.contract_addr, str)
    assert json.loads(node_info.contract_addr) == DEPLOY_RETURN


def test_create_shared_tenant_wires_encode_contract_addr_at_the_call_site(monkeypatch, tmp_path):
    """Same guard for the shared-node path: drives the real
    sui_shared.create_shared_tenant instead of composing the pipeline by hand."""

    fake_state = {
        "package_id": "0xpkg-shared",
        "admins": [{"address": "0xadmin", "config": "/fake/admin/client.yaml"}],
        "rpc_url": "http://127.0.0.1:9000",
        "faucet_url": "http://127.0.0.1:9123",
        "rpc_port": 9000,
    }

    # Redirects the keystore/lock-file writes create_shared_tenant does on the
    # way to a tmp dir instead of the real /tmp/sui-shared.
    monkeypatch.setattr(sui_shared, "SUI_SHARED_ROOT", tmp_path)
    monkeypatch.setattr(sui_shared, "bootstrap", lambda: fake_state)
    monkeypatch.setattr(
        sui_shared, "create_sui_keypair",
        lambda label: {"address": "0xplayer", "base64_key": "fake-b64", "bech32_key": "fake-bech32"},
    )
    monkeypatch.setattr(sui_shared, "write_client_config", lambda *a, **k: None)
    monkeypatch.setattr(sui_shared, "_fund_player", lambda *a, **k: None)
    monkeypatch.setattr(sui_shared, "shared_node_pid", lambda: 4321)

    node_info = sui_shared.create_shared_tenant("test-team", _fake_deploy_handler)

    assert isinstance(node_info.contract_addr, str)
    assert json.loads(node_info.contract_addr) == DEPLOY_RETURN
