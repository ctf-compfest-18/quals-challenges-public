import json

import pytest

from sandbox import sui_shared


def _stub_admin_factory():
    def _create_admin(index):
        return {"address": f"0xadmin{index}", "config": f"/fake/admin-{index}/client.yaml"}

    return _create_admin


def _patch_bootstrap_collaborators(monkeypatch, tmp_path, *, admin_count=3):
    """Stub every bootstrap() collaborator except the cap-minting logic under
    test, and sandbox its filesystem writes to tmp_path."""
    monkeypatch.setattr(sui_shared, "SUI_SHARED_ROOT", tmp_path)
    monkeypatch.setattr(sui_shared, "_STATE_FILE", tmp_path / "state.json")
    monkeypatch.setattr(sui_shared, "_BOOTSTRAP_LOCK", tmp_path / "bootstrap.lock")
    monkeypatch.setattr(sui_shared, "SUI_ADMIN_ACCOUNTS", admin_count)
    monkeypatch.setattr(sui_shared, "wait_for_shared_node", lambda timeout=180: None)
    monkeypatch.setattr(sui_shared, "_create_admin", _stub_admin_factory())
    monkeypatch.setattr(sui_shared, "publish_package", lambda *a, **k: "0xpkg")


def test_bootstrap_mints_a_distinct_cap_per_admin_and_persists_it(monkeypatch, tmp_path):
    """admins[0] gets the cap `init` granted the publisher; every other admin
    gets one minted by admins[0] and transferred to it. All must end up
    distinct, correctly attributed, and on disk -- not just held in memory."""
    _patch_bootstrap_collaborators(monkeypatch, tmp_path, admin_count=3)

    lookup_calls = []
    mint_calls = []

    def fake_get_object_id_by_type(rpc_url, owner_addr, package_id, module, struct):
        assert (package_id, module, struct) == ("0xpkg", "setup", "AdminCap")
        lookup_calls.append(owner_addr)
        return f"0xcap-{owner_addr}"

    def fake_call_function(client_config, package_id, module, function, *, args=None, gas_budget=None):
        assert (module, function) == ("setup", "mint_cap")
        mint_calls.append({"client_config": client_config, "args": args})
        return {"minted_to": args[1]}

    def fake_find_object(tx_result, package_id, module, struct, *, shared=False, owned=False):
        assert (module, struct, owned) == ("setup", "AdminCap", True)
        return f"0xcap-{tx_result['minted_to']}"

    monkeypatch.setattr(sui_shared, "get_object_id_by_type", fake_get_object_id_by_type)
    monkeypatch.setattr(sui_shared, "call_function", fake_call_function)
    monkeypatch.setattr(sui_shared, "find_object", fake_find_object)

    state = sui_shared.bootstrap()
    admins = state["admins"]

    # admins[0]'s cap comes from the publish-time lookup, not a mint call --
    # exactly one lookup, for admins[0] alone.
    assert lookup_calls == ["0xadmin0"]
    assert admins[0]["cap"] == "0xcap-0xadmin0"

    # Every other admin's cap was minted by admins[0] (the only signer with a
    # cap at that point) and addressed to that specific admin, in order.
    assert [c["client_config"] for c in mint_calls] == ["/fake/admin-0/client.yaml"] * 2
    assert [c["args"][1] for c in mint_calls] == ["0xadmin1", "0xadmin2"]
    assert admins[1]["cap"] == "0xcap-0xadmin1"
    assert admins[2]["cap"] == "0xcap-0xadmin2"

    # No two admins share a cap.
    assert len({a["cap"] for a in admins}) == len(admins)

    # The mint results are persisted, not only returned in-memory: a bug that
    # mutated the admin dicts after _write_state() ran would pass every
    # assertion above yet fail this one.
    persisted = json.loads((tmp_path / "state.json").read_text())
    assert [a["cap"] for a in persisted["admins"]] == [a["cap"] for a in admins]


def test_bootstrap_raises_if_publish_did_not_grant_admin0_a_cap(monkeypatch, tmp_path):
    _patch_bootstrap_collaborators(monkeypatch, tmp_path, admin_count=2)
    monkeypatch.setattr(sui_shared, "get_object_id_by_type", lambda *a, **k: None)
    monkeypatch.setattr(sui_shared, "call_function", lambda *a, **k: {})
    monkeypatch.setattr(sui_shared, "find_object", lambda *a, **k: "0xcap-unused")

    with pytest.raises(RuntimeError, match="0xadmin0"):
        sui_shared.bootstrap()


def test_bootstrap_raises_if_mint_cap_produces_no_discoverable_cap(monkeypatch, tmp_path):
    _patch_bootstrap_collaborators(monkeypatch, tmp_path, admin_count=2)
    monkeypatch.setattr(sui_shared, "get_object_id_by_type", lambda *a, **k: "0xcap-0xadmin0")
    monkeypatch.setattr(sui_shared, "call_function", lambda *a, **k: {})
    monkeypatch.setattr(sui_shared, "find_object", lambda *a, **k: None)

    with pytest.raises(RuntimeError, match="0xadmin1"):
        sui_shared.bootstrap()
