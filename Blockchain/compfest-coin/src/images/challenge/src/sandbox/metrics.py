from __future__ import annotations

import threading
from typing import Any


_lock = threading.Lock()
_counters: dict[str, int] = {
    "launch_success": 0,
    "launch_fail": 0,
    "kill_total": 0,
    "capacity_reject": 0,
    "reap_expired": 0,
    "reap_dead": 0,
}
_launch_latency_ms: list[float] = []


def inc(name: str, n: int = 1) -> None:
    with _lock:
        _counters[name] = _counters.get(name, 0) + n


def observe_launch_ms(ms: float) -> None:
    with _lock:
        _launch_latency_ms.append(float(ms))
        if len(_launch_latency_ms) > 100:
            del _launch_latency_ms[:-100]


def snapshot() -> dict[str, Any]:
    with _lock:
        vals = list(_launch_latency_ms)
        ctr = dict(_counters)
    if vals:
        ordered = sorted(vals)
        p95 = ordered[int(0.95 * (len(ordered) - 1))]
        avg = sum(ordered) / len(ordered)
    else:
        p95 = 0.0
        avg = 0.0
    return {
        **ctr,
        "launch_latency_ms_p95": p95,
        "launch_latency_ms_avg": avg,
        "launch_samples": len(vals),
    }
