from __future__ import annotations

import os
import time

from .type import NodeInfo


def stamp_lease(node_info: NodeInfo, ttl_seconds: int) -> NodeInfo:
    now = time.time()
    node_info.created_at = now
    node_info.expires_at = now + ttl_seconds
    node_info.status = node_info.status or "ready"
    return node_info


def is_expired(node_info: NodeInfo, now: float | None = None) -> bool:
    expires_at = getattr(node_info, "expires_at", None)
    if expires_at is None:
        return False
    return (now if now is not None else time.time()) >= float(expires_at)


def pid_alive(pid: int) -> bool:
    if not pid or pid <= 0:
        return False
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
