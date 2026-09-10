import sandbox.sui_shared as sui_shared


def test_admin_pay_rebuilds_a_transaction_rejected_for_a_stale_coin(monkeypatch):
    coin_sets = [["0xold"], ["0xcurrent"]]
    calls = []

    monkeypatch.setattr(sui_shared, "_gas_coin_ids", lambda _config: coin_sets.pop(0))
    monkeypatch.setattr(sui_shared.time, "sleep", lambda _seconds: None)

    def fake_run_checked(args, **_kwargs):
        calls.append(args)
        if len(calls) == 1:
            raise RuntimeError(
                "Transaction needs to be rebuilt because object 0xold version 0x8 is unavailable"
            )

    monkeypatch.setattr(sui_shared, "run_checked", fake_run_checked)

    sui_shared._admin_pay("/admin/client.yaml", "0xplayer", 10_000)

    assert calls[0][calls[0].index("--input-coins") + 1] == "0xold"
    assert calls[1][calls[1].index("--input-coins") + 1] == "0xcurrent"


def test_admin_pay_does_not_retry_other_errors(monkeypatch):
    monkeypatch.setattr(sui_shared, "_gas_coin_ids", lambda _config: ["0xcoin"])
    monkeypatch.setattr(sui_shared, "run_checked", lambda *_args, **_kwargs: (_ for _ in ()).throw(RuntimeError("RPC down")))

    try:
        sui_shared._admin_pay("/admin/client.yaml", "0xplayer", 10_000)
    except RuntimeError as exc:
        assert str(exc) == "RPC down"
    else:
        raise AssertionError("expected the original failure to be raised")
