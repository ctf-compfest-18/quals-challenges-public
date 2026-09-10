import os
import time

from sandbox import lifecycle, metrics


def test_is_expired_when_past_expires_at():
    assert lifecycle.is_expired(type("N", (), {"expires_at": time.time() - 10})()) is True


def test_is_expired_false_when_future():
    assert lifecycle.is_expired(type("N", (), {"expires_at": time.time() + 1000})()) is False


def test_is_expired_false_when_missing():
    assert lifecycle.is_expired(type("N", (), {"expires_at": None})()) is False


def test_pid_alive_current_process():
    assert lifecycle.pid_alive(os.getpid()) is True


def test_pid_alive_invalid():
    assert lifecycle.pid_alive(0) is False
    assert lifecycle.pid_alive(-1) is False


def test_metrics_inc_and_observe():
    before = metrics.snapshot()
    metrics.inc("launch_success")
    metrics.observe_launch_ms(12.5)
    after = metrics.snapshot()
    assert after["launch_success"] == before.get("launch_success", 0) + 1
    assert after["launch_samples"] >= 1
