import time

from sandbox.lifecycle import is_expired, pid_alive


class _N:
    def __init__(self, expires_at):
        self.expires_at = expires_at


def test_is_expired_when_past_expires_at():
    assert is_expired(_N(time.time() - 10)) is True


def test_is_expired_false_when_future():
    assert is_expired(_N(time.time() + 1000)) is False


def test_is_expired_false_when_missing():
    assert is_expired(_N(None)) is False


def test_pid_alive_current_process():
    import os

    assert pid_alive(os.getpid()) is True


def test_pid_alive_invalid():
    assert pid_alive(0) is False
    assert pid_alive(-1) is False
