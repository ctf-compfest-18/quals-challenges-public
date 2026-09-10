import os
import re
import logging
import traceback
import requests
import time
import threading
from functools import wraps
from typing import Callable, Any
import websockets
from websockets.client import connect
import asyncio
import json
import os.path

from flask import Flask, Response, request, session, send_file, jsonify, send_from_directory
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from flask_sock import Sock

from .env import SESSION_COOKIE_NAME, SECRET_KEY # type: ignore
from .ppow import Challenge, check
from .blockchain_manager import (
    BLOCKCHAIN_MANAGER,
    INSTANCE_TTL_SECONDS,
    InstanceCapacityError,
    NodeInfo,
    SUI_MAX_ACTIVE,
    active_instance_count,
    foreign_object_ids,
    instance_exists,
    load_instance,
    normalize_sui_id,
    team_instance_exists,
)
from . import metrics as ctf_metrics

# In-memory async launch jobs (single worker ownership).
_LAUNCH_JOBS: dict[str, dict] = {}

class AppConfig:
    """Centralized application configuration"""
    # Network settings
    HTTP_PORT = int(os.getenv("HTTP_PORT", 8545))
    LAUNCHER_PORT = int(os.getenv("LAUNCHER_PORT", 8546))
    ORIGIN = os.getenv("ORIGIN", "http://localhost")
    
    # Security settings
    SECRET_KEY = SECRET_KEY
    SESSION_COOKIE_NAME = SESSION_COOKIE_NAME
    DISABLE_TICKET = os.getenv("DISABLE_TICKET", "false").lower() == "true"
    FLAG = os.getenv("FLAG", "PCTF{placeholder}")
    CHALLENGE_LEVEL = int(os.getenv("CHALLENGE_LEVEL", 10000))
    RATE_LIMIT = os.getenv("RATE_LIMIT", "360 per minute")
    SUI_ALLOWED_PREFIXES = [
        value.strip()
        for value in os.getenv("SUI_ALLOWED_PREFIXES", "sui_,suix_,unsafe_,rpc.").split(",")
        if value.strip()
    ]
    SUI_BLOCKED_METHODS = [
        value.strip()
        for value in os.getenv(
            "SUI_BLOCKED_METHODS",
            "sui_dryRunTransactionBlock,unsafe_devInspectTransactionBlock",
        ).split(",")
        if value.strip()
    ]
    RPC_PROXY_TIMEOUT = float(os.getenv("RPC_PROXY_TIMEOUT", "30"))
    
    # Validation patterns
    UUID_PATTERN = re.compile(r'^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$')
    ALPHANUMERIC_PATTERN = re.compile(r'^[a-zA-Z0-9]{1,}$')
    
    # Blockchain method restrictions
    BLOCKCHAIN_RULES = {
        "eth": {
            "allowed_namespaces": ["web3", "eth", "net"],
            "blocked_methods": ["eth_sendUnsignedTransaction"]
        },
        "cairo": {
            "allowed_namespaces": ["starknet"],
            "blocked_methods": []
        },
        "solana": {
            "blocked_namespaces": ["requestAirdrop"],
            "blocked_methods": []
        },
        "sui": {
            "allowed_prefixes": SUI_ALLOWED_PREFIXES,
            "blocked_methods": SUI_BLOCKED_METHODS
        }
    }
    
    # Security headers
    SECURITY_HEADERS = {
        "X-Content-Type-Options": "nosniff",
        "X-Frame-Options": "DENY",
        # "Content-Security-Policy": "default-src 'self'",
        "Referrer-Policy": "strict-origin-when-cross-origin"
    }

config = AppConfig()

# Flask application setup
app = Flask(__name__, static_folder="frontend", static_url_path="/static")
sock = Sock(app)
app.secret_key = config.SECRET_KEY
app.config['SESSION_COOKIE_NAME'] = config.SESSION_COOKIE_NAME
app.config['SESSION_COOKIE_SECURE'] = False
app.config['SESSION_COOKIE_HTTPONLY'] = True
app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'

# Rate limiting setup
limiter = Limiter(
    app=app,
    key_func=get_remote_address,
    default_limits=[config.RATE_LIMIT],
    storage_uri="memory://",
)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("BlockchainGateway")

# Decorators and helpers
def validate_session(f):
    """Validate session ticket presence"""
    @wraps(f)
    def wrapper(*args, **kwargs):
        if not config.DISABLE_TICKET and not session.get("ticket"):
            return error_response("Authentication required", 401)
        return f(*args, **kwargs)
    return wrapper

def error_response(message: str, code: int = 400) -> Response:
    """Standard error response format"""
    return jsonify({
        "success": False,
        "error": message,
        "code": code
    }), code

def jsonrpc_error(code: int, message: str, request_id: Any = None) -> Response:
    """JSON-RPC error response format"""
    return jsonify({
        "jsonrpc": "2.0",
        "error": {"code": code, "message": message},
        "id": request_id
    })

def iter_jsonrpc_requests(payload: Any):
    if isinstance(payload, list):
        return payload
    return [payload]

def first_jsonrpc_id(payload: Any) -> Any:
    for item in iter_jsonrpc_requests(payload):
        if isinstance(item, dict):
            return item.get("id")
    return None

def validate_jsonrpc_shape(payload: Any) -> tuple[bool, Any]:
    requests_to_validate = iter_jsonrpc_requests(payload)
    if not requests_to_validate:
        return False, None
    for item in requests_to_validate:
        if not isinstance(item, dict) or "method" not in item:
            return False, first_jsonrpc_id(payload)
    return True, first_jsonrpc_id(payload)

def upstream_headers() -> dict[str, str]:
    excluded = {
        "connection",
        "content-length",
        "host",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
    }
    return {
        key: value
        for key, value in request.headers.items()
        if key.lower() not in excluded
    }

# Request lifecycle handlers
@app.before_request
def initialize_session():
    """Initialize session with challenge and ticket"""
    if config.DISABLE_TICKET and "ticket" not in session:
        session["ticket"] = os.urandom(16).hex()
        
    if "challenge" not in session:
        session["challenge"] = str(Challenge.generate(config.CHALLENGE_LEVEL))

@app.after_request
def add_security_headers(response: Response) -> Response:
    """Add security headers to all responses"""
    for header, value in config.SECURITY_HEADERS.items():
        response.headers[header] = value
    return response

# Core application routes
@app.route("/solution", methods=["POST"])
@limiter.limit("5 per minute")
def handle_solution():
    """Process proof-of-work challenge solution"""
    try:
        solution = request.json.get("solution", "")
        challenge = Challenge.from_string(session["challenge"])
        
        if not check(challenge, solution):
            raise ValueError("Invalid solution")
            
        session["ticket"] = os.urandom(16).hex()
        session.pop("challenge", None)
        return jsonify({"message": "Challenge solved successfully"})
        
    except Exception as e:
        session["challenge"] = str(Challenge.generate(config.CHALLENGE_LEVEL))
        logger.warning(f"Challenge failed: {str(e)}")
        return error_response("Challenge verification failed", 401)

@app.route("/launch", methods=["POST"])
@validate_session
@limiter.limit("6 per minute")
async def launch_instance():
    """Handle blockchain instance launch.

    Sync is the default (CTF UI). Set SUI_ASYNC_LAUNCH=true or pass ?async=1
    to get HTTP 202 + job_id and poll GET /launch/status/<job_id>.
    """
    started = time.time()
    async_mode = (
        os.getenv("SUI_ASYNC_LAUNCH", "false").lower() in {"1", "true", "yes"}
        or request.args.get("async", "").lower() in {"1", "true", "yes"}
    )
    try:
        deploy_handler = app.config.get("DEPLOY_HANDLER")
        if not deploy_handler:
            raise RuntimeError("Deployment handler not configured")

        if async_mode:
            job_id = os.urandom(8).hex()
            ticket = session["ticket"]
            _LAUNCH_JOBS[job_id] = {
                "status": "pending",
                "team": ticket,
                "created_at": time.time(),
                "error": None,
                "data": None,
            }

            def run_job():
                t0 = time.time()
                try:
                    _LAUNCH_JOBS[job_id]["status"] = "running"
                    # start_instance is async; run in a fresh event loop in this thread.
                    node_info = asyncio.run(
                        BLOCKCHAIN_MANAGER.start_instance(ticket, deploy_handler)
                    )
                    data = generate_session_data(node_info)
                    _LAUNCH_JOBS[job_id]["status"] = "ready"
                    _LAUNCH_JOBS[job_id]["data"] = data
                    _LAUNCH_JOBS[job_id]["uuid"] = node_info.uuid
                    ctf_metrics.inc("launch_success")
                    ctf_metrics.observe_launch_ms((time.time() - t0) * 1000.0)
                except InstanceCapacityError as e:
                    _LAUNCH_JOBS[job_id]["status"] = "failed"
                    _LAUNCH_JOBS[job_id]["error"] = str(e)
                    _LAUNCH_JOBS[job_id]["code"] = 429
                    ctf_metrics.inc("launch_fail")
                except Exception as e:
                    _LAUNCH_JOBS[job_id]["status"] = "failed"
                    _LAUNCH_JOBS[job_id]["error"] = str(e)
                    _LAUNCH_JOBS[job_id]["code"] = 500
                    ctf_metrics.inc("launch_fail")
                    traceback.print_exc()

            threading.Thread(target=run_job, name=f"launch-{job_id}", daemon=True).start()
            return jsonify({
                "success": True,
                "async": True,
                "job_id": job_id,
                "status": "pending",
                "poll": f"/launch/status/{job_id}",
            }), 202

        node_info = await BLOCKCHAIN_MANAGER.start_instance(session["ticket"], deploy_handler)
        session["data"] = generate_session_data(node_info)
        ctf_metrics.inc("launch_success")
        ctf_metrics.observe_launch_ms((time.time() - started) * 1000.0)
        return jsonify({
            "success": True,
            **session["data"],
        })
    except InstanceCapacityError as e:
        ctf_metrics.inc("launch_fail")
        logger.warning(f"Instance capacity full: {e}")
        return error_response(str(e), 429)
    except RuntimeError as e:
        ctf_metrics.inc("launch_fail")
        msg = str(e)
        code = 409 if "already exists" in msg.lower() else 500
        traceback.print_exc()
        logger.error(f"Instance launch failed: {msg}")
        return error_response(f"Instance launch failed: {msg}", code)
    except Exception as e:
        ctf_metrics.inc("launch_fail")
        traceback.print_exc()
        logger.error(f"Instance launch failed: {str(e)}")
        return error_response(f"Instance launch failed: {str(e)}", 500)


@app.route("/launch/status/<job_id>")
@validate_session
def launch_status(job_id: str):
    """Poll async launch job. When ready, mirrors session data into the session."""
    job = _LAUNCH_JOBS.get(job_id)
    if not job:
        return error_response("Unknown job_id", 404)
    if job.get("team") and job["team"] != session.get("ticket"):
        return error_response("Job does not belong to this session", 403)
    payload = {
        "success": job["status"] == "ready",
        "job_id": job_id,
        "status": job["status"],
        "error": job.get("error"),
    }
    if job["status"] == "ready" and job.get("data"):
        session["data"] = job["data"]
        payload.update(job["data"])
    if job["status"] == "failed":
        return jsonify(payload), int(job.get("code") or 500)
    return jsonify(payload)

@app.route("/kill", methods=["POST"])
@validate_session
def kill_instance():
    """Terminate blockchain instance"""
    try:
        BLOCKCHAIN_MANAGER.terminate_instance(session["ticket"])
        session["data"] = None
        ctf_metrics.inc("kill_total")
        return jsonify({
            "success": True,
            "message": "Instance terminated successfully"
        })
        
    except Exception as e:
        logger.error(f"Instance termination failed: {str(e)}")
        return error_response(f"Instance termination failed: {str(e)}", 500)

@app.route("/flag")
@validate_session
@limiter.limit("6 per minute")
async def get_flag():
    """Retrieve flag after successful challenge solution"""
    try:
        if not await BLOCKCHAIN_MANAGER.verify_solution(session["ticket"]):
            return error_response("Challenge not solved yet", 403)
            
        return jsonify({
            "success": True,
            "flag": config.FLAG,
            "message": "Congratulations!"
        })
        
    except Exception as e:
        logger.error(f"Flag retrieval failed: {str(e)}")
        return error_response("Flag verification failed", 500)

_SUI_ID_PATTERN = re.compile(rb"0x[0-9a-fA-F]{1,64}(?![0-9a-fA-F])")


def _foreign_ids_in_request(raw_body: bytes, uuid: str) -> set[str]:
    """IDs in this request that belong to a different live tenant."""
    if not raw_body:
        return set()
    # Flask <uuid:uuid> yields uuid.UUID; index keys are strings.
    uuid = str(uuid)
    foreign = foreign_object_ids(uuid)
    if not foreign:
        return set()
    referenced = {
        normalize_sui_id(match.group(0).decode("ascii"))
        for match in _SUI_ID_PATTERN.finditer(raw_body)
    }
    return referenced & foreign


@app.route("/<uuid:uuid>", methods=["POST"])
@app.route("/<uuid:uuid>/", methods=["POST"])
@limiter.exempt
def proxy_request(uuid: str):
    """Proxy HTTP requests to blockchain nodes"""
    data = None
    try:
        raw_body = request.get_data()
        data = request.get_json(silent=True)
        blockchain_type = BLOCKCHAIN_MANAGER.blockchain_type
        rules = config.BLOCKCHAIN_RULES.get(blockchain_type, {})
        
        # Validate JSON-RPC request
        valid, request_id = validate_jsonrpc_shape(data)
        if not valid:
            return jsonrpc_error(-32600, "Invalid request", request_id)

        # Validate node exists
        if not instance_exists(uuid):
            return jsonrpc_error(-32602, "Invalid instance ID", request_id)
            
        node_info = load_instance(uuid)
            
        # Validate method permissions
        for rpc_request in iter_jsonrpc_requests(data):
            method = rpc_request["method"]
            if blockchain_type == "eth":
                allowed = any(method.startswith(ns) for ns in rules["allowed_namespaces"])
                blocked = method in rules["blocked_methods"]
                if not allowed or blocked:
                    return jsonrpc_error(-32601, "Method not allowed", rpc_request.get("id"))
                # pre-tx hook
                if method == "eth_sendTransaction" or method == "eth_sendRawTransaction":
                    pre_tx_hook = app.config.get("PRE_TX_HOOK")
                    if pre_tx_hook:
                        status, msg = pre_tx_hook(data, node_info=node_info)
                        if status // 100 != 2:
                            return jsonrpc_error(status, msg, rpc_request.get("id"))

            elif blockchain_type == "solana":
                if any(method.startswith(ns) for ns in rules["blocked_namespaces"]):
                    return jsonrpc_error(-32601, "Method not allowed", rpc_request.get("id"))

            elif blockchain_type == "sui":
                allowed_prefixes = rules.get("allowed_prefixes", [])
                blocked_methods = rules.get("blocked_methods", [])
                if method in blocked_methods:
                    return jsonrpc_error(-32601, "Method not allowed", rpc_request.get("id"))
                if not any(method.startswith(prefix) for prefix in allowed_prefixes):
                    return jsonrpc_error(-32601, "Method not allowed", rpc_request.get("id"))

        # On a shared node every tenant's objects live on the same chain, so a
        # team that learns another's object IDs could grief them by acting on
        # another tenant's shared objects. (They cannot steal a flag: an owned
        # object is already protected by Sui's ownership model.) Reject any
        # request that names an object belonging to a different live tenant.
        if blockchain_type == "sui":
            trespass = _foreign_ids_in_request(raw_body, uuid)
            if trespass:
                ctf_metrics.inc("isolation_reject")
                logger.warning("instance %s referenced foreign objects: %s", uuid, sorted(trespass))
                return jsonrpc_error(
                    -32602, "Request references another instance's objects", request_id
                )


        # Forward request to node
        response = requests.post(
            f"http://127.0.0.1:{node_info.port}/",
            data=raw_body,
            headers=upstream_headers(),
            timeout=config.RPC_PROXY_TIMEOUT,
        )
        
        # post-tx hook
        if blockchain_type == "eth":
            methods = [item["method"] for item in iter_jsonrpc_requests(data)]
            if "eth_sendTransaction" in methods or "eth_sendRawTransaction" in methods:
                post_tx_hook = app.config.get("POST_TX_HOOK")
                if post_tx_hook:
                    status, msg = post_tx_hook(data, response, node_info=node_info)
                    if status // 100 != 2:
                        return jsonrpc_error(status, msg, request_id)

        hop_by_hop = {
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "content-length",
            "content-encoding",
        }
        safe_headers = {
            key: value
            for key, value in response.headers.items()
            if key.lower() not in hop_by_hop
        }
        safe_headers["Connection"] = "close"
        return Response(
            response.content,
            status=response.status_code,
            headers=safe_headers,
            content_type=response.headers.get("Content-Type", "application/json"),
        )

    except requests.exceptions.RequestException as e:
        logger.error(f"Node communication error: {str(e)}")
        return jsonrpc_error(-32000, "Backend service unavailable", first_jsonrpc_id(data))

@sock.route("/<uuid:uuid>")
@limiter.limit("240 per minute")
async def proxy_websocket(ws: websockets.WebSocketServerProtocol, uuid: str):
    """Proxy WebSocket connections to blockchain nodes"""
    try:
        if not instance_exists(uuid) or BLOCKCHAIN_MANAGER.blockchain_type != "solana":
            ws.send(json.dumps({
                "jsonrpc": "2.0",
                "error": {"code": -32602, "message": "Invalid instance ID"},
                "id": None
            }))
            return

        node_info = load_instance(uuid)
        blockchain_type = BLOCKCHAIN_MANAGER.blockchain_type
        rules = config.BLOCKCHAIN_RULES.get(blockchain_type, {})

        async with connect(f"ws://127.0.0.1:{int(node_info.port)+1}/") as node_ws:
            while True:
                message = ws.receive()
                data = json.loads(message)
                
                # Validate method permissions
                if "method" in data:
                    method = data["method"]
                    if blockchain_type == "solana":
                        if any(method.startswith(ns) for ns in rules["blocked_namespaces"]):
                            ws.send(json.dumps({
                                "jsonrpc": "2.0",
                                "error": {"code": -32601, "message": "Method not allowed"},
                                "id": data.get("id")
                            }))
                            continue
                
                try:
                    await asyncio.wait_for(node_ws.send(message), timeout=1)
                    message = await asyncio.wait_for(node_ws.recv(), timeout=1)
                    ws.send(message)
                except asyncio.TimeoutError:
                    logger.warning("WebSocket operation timed out after 1 seconds")
                    continue
                except Exception as e:
                    logger.error(f"WebSocket operation error: {str(e)}")
                    continue

    except Exception as e:
        logger.error(f"WebSocket proxy error: {str(e)}")
        traceback.print_exc()
        try:
            await ws.send(json.dumps({
                "jsonrpc": "2.0",
                "error": {"code": -32000, "message": "Backend service unavailable"},
                "id": None
            }))
        except:
            pass

# Supporting routes
@app.route("/health")
@limiter.exempt
def health():
    """Liveness probe for Docker/orchestrators."""
    return jsonify({
        "ok": True,
        "blockchain": BLOCKCHAIN_MANAGER.blockchain_type,
    })


@app.route("/ready")
@limiter.exempt
def ready():
    """Readiness: capacity remains and warm path is usable when pool is enabled."""
    if BLOCKCHAIN_MANAGER.blockchain_type != "sui":
        return jsonify({"ready": True})

    from .sui_helper import SUI_ISOLATION_MODE, SUI_POOL_SIZE, pool_counts

    active = active_instance_count()
    has_capacity = SUI_MAX_ACTIVE <= 0 or active < SUI_MAX_ACTIVE

    if SUI_ISOLATION_MODE == "shared":
        # Every launch needs the node up and the package published, so neither
        # being done yet means not ready -- there is no cold path to fall back on.
        from .sui_shared import shared_bootstrap_state

        state = shared_bootstrap_state()
        is_ready = bool(has_capacity and state["node_up"] and state["package_id"])
        return jsonify({
            "ready": is_ready,
            "active": active,
            "max_active": SUI_MAX_ACTIVE,
            "mode": "shared",
            "node_up": state["node_up"],
            "package_published": bool(state["package_id"]),
            "package_id": state["package_id"],
        }), (200 if is_ready else 503)

    counts = pool_counts()
    # Cold create is always available under capacity; warm is an optimization signal.
    warm_ready = counts["ready"] > 0
    is_ready = bool(has_capacity)
    code = 200 if is_ready else 503
    return jsonify({
        "ready": is_ready,
        "warm_ready": warm_ready,
        "active": active,
        "max_active": SUI_MAX_ACTIVE,
        "pool": counts,
    }), code


@app.route("/metrics")
@limiter.exempt
def metrics_endpoint():
    """Prometheus text format for ops scrapes."""
    if BLOCKCHAIN_MANAGER.blockchain_type == "sui":
        from .sui_helper import pool_counts
        counts = pool_counts()
    else:
        counts = {"ready": 0, "leased": 0, "target": 0}

    active = active_instance_count()
    snap = ctf_metrics.snapshot()
    lines = [
        "# HELP ctf_active_instances Active team instances",
        "# TYPE ctf_active_instances gauge",
        f"ctf_active_instances {active}",
        "# HELP ctf_max_active_instances Configured max active",
        "# TYPE ctf_max_active_instances gauge",
        f"ctf_max_active_instances {SUI_MAX_ACTIVE}",
        "# HELP ctf_sui_pool_ready Warm pool ready nodes",
        "# TYPE ctf_sui_pool_ready gauge",
        f"ctf_sui_pool_ready {counts['ready']}",
        "# HELP ctf_sui_pool_leased Pool leased records",
        "# TYPE ctf_sui_pool_leased gauge",
        f"ctf_sui_pool_leased {counts['leased']}",
        "# HELP ctf_sui_pool_target Configured pool size",
        "# TYPE ctf_sui_pool_target gauge",
        f"ctf_sui_pool_target {counts['target']}",
        "# HELP ctf_instance_ttl_seconds Instance TTL",
        "# TYPE ctf_instance_ttl_seconds gauge",
        f"ctf_instance_ttl_seconds {INSTANCE_TTL_SECONDS}",
        "# HELP ctf_launch_success_total Successful launches",
        "# TYPE ctf_launch_success_total counter",
        f"ctf_launch_success_total {snap.get('launch_success', 0)}",
        "# HELP ctf_launch_fail_total Failed launches",
        "# TYPE ctf_launch_fail_total counter",
        f"ctf_launch_fail_total {snap.get('launch_fail', 0)}",
        "# HELP ctf_capacity_reject_total Capacity rejects",
        "# TYPE ctf_capacity_reject_total counter",
        f"ctf_capacity_reject_total {snap.get('capacity_reject', 0)}",
        "# HELP ctf_kill_total Instance kills",
        "# TYPE ctf_kill_total counter",
        f"ctf_kill_total {snap.get('kill_total', 0)}",
        "# HELP ctf_launch_latency_ms_p95 Launch latency p95",
        "# TYPE ctf_launch_latency_ms_p95 gauge",
        f"ctf_launch_latency_ms_p95 {snap.get('launch_latency_ms_p95', 0)}",
        "# HELP ctf_launch_samples Launch latency samples",
        "# TYPE ctf_launch_samples gauge",
        f"ctf_launch_samples {snap.get('launch_samples', 0)}",
        "# HELP ctf_reap_expired_total Expired instances reaped",
        "# TYPE ctf_reap_expired_total counter",
        f"ctf_reap_expired_total {snap.get('reap_expired', 0)}",
        "# HELP ctf_reap_dead_total Dead PID instances reaped",
        "# TYPE ctf_reap_dead_total counter",
        f"ctf_reap_dead_total {snap.get('reap_dead', 0)}",
    ]
    return Response("\n".join(lines) + "\n", mimetype="text/plain; version=0.0.4")


@app.route("/data")
def get_instance_data():
    """Retrieve instance metadata"""
    return jsonify(session.get("data", {}))

@app.route("/status")
@validate_session
def get_instance_status():
    """Check if an instance is running for the current session"""
    try:
        running = team_instance_exists(session["ticket"])
        return jsonify({
            "success": True,
            "running": running
        })
    except Exception as e:
        logger.error(f"Status check failed: {str(e)}")
        return error_response(f"Status check failed: {str(e)}", 500)

@app.route("/challenge")
def get_current_challenge():
    """Get current proof-of-work challenge"""
    return jsonify({"challenge": session.get("challenge")})

@app.route("/")
def serve_frontend():
    """Serve static frontend interface"""
    return send_file("frontend/index.html")

@app.route("/<path:path>")
def serve_static(path):
    """Serve static files from the frontend directory"""
    try:
        # Prevent directory traversal attacks
        if '..' in path:
            return error_response("Invalid path", 403)
            
        # Handle Next.js static files
        if path.startswith('_next/'):
            return send_from_directory("frontend", path)
            
        # Check if file exists
        file_path = os.path.join("frontend", path)
        if os.path.isfile(file_path):
            return send_file(file_path)
            
        # If it's a UUID format, return the SPA frontend
        # to let the client-side router handle it
        if config.UUID_PATTERN.match(path):
            return send_file("frontend/index.html")
            
        # Return the SPA for all other routes that don't match files
        # This allows the Next.js client-side router to handle routes
        if not path.endswith(('.html', '.css', '.js', '.json', '.ico', '.png', '.jpg', '.svg', '.woff', '.woff2')):
            return send_file("frontend/index.html")
            
        # File not found
        return send_file("frontend/404.html"), 404
    except Exception as e:
        logger.error(f"Error serving static file: {path}, error: {str(e)}")
        return error_response("Resource not found", 404)

# Helper functions
def generate_session_data(node_info: NodeInfo) -> dict:
    """Generate standardized session data structure"""
    ttl_min = max(1, int(INSTANCE_TTL_SECONDS) // 60)
    base_data = {
        "0": {
            "RPC_URL": f"{{ORIGIN}}/{node_info.uuid}",
            "WS_URL": f"ws://{{ORIGIN}}/{node_info.uuid}/ws"
        },
        "message": (
            f"Your private blockchain has been deployed. "
            f"It will automatically terminate in {ttl_min} minutes."
        ),
    }
    
    if BLOCKCHAIN_MANAGER.blockchain_type == "solana":
        base_data.update({
            "1": {"PLAYER_KEYPAIR": node_info.accounts[1].private_key},
            "2": {"CTX_PUBKEY": node_info.accounts[2].public_key},
            "3": {"PROGRAM_ID": node_info.contract_addr},
        })
    elif BLOCKCHAIN_MANAGER.blockchain_type == "sui":
        contract_data = json.loads(node_info.contract_addr) if node_info.contract_addr else {}
        entries = {
            "PRIVKEY": node_info.accounts[1].private_key,
            "WALLET_ADDR": node_info.accounts[1].address,
            "PACKAGE_ID": contract_data.get("package_id", ""),
            "SETUP_ID": contract_data.get("setup", ""),
        }
        entries.update(contract_data.get("objects", {}))
        base_data.update({str(i): {k: v} for i, (k, v) in enumerate(entries.items(), start=1)})
    else:
        base_data.update({
            "1": {"PRIVKEY": node_info.accounts[1].private_key},
            "2": {"SETUP_CONTRACT_ADDR": node_info.contract_addr},
            "3": {"WALLET_ADDR": node_info.accounts[1].address},
        })
    
    return base_data

# Error handlers
@app.errorhandler(404)
def not_found(e):
    return error_response("Resource not found", 404)

@app.errorhandler(500)
def internal_error(e):
    return error_response("Internal server error", 500)

@app.errorhandler(Exception)
def handle_exceptions(e):
    logger.exception(f"Unhandled exception occurred: {str(e)}")
    return error_response(f"An unexpected error occurred: {str(e)}", 500)

# Application initialization
def run_launcher(
    deploy_handler: Callable,
    pre_tx_hook: Callable = None, post_tx_hook: Callable = None
) -> Flask:
    """Initialize and run the application"""
    app.config["DEPLOY_HANDLER"] = deploy_handler
    
    app.config["PRE_TX_HOOK"] = pre_tx_hook
    app.config["POST_TX_HOOK"] = post_tx_hook
    
    return app
