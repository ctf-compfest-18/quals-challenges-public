#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, urlparse

import requests
import yaml


class DeployError(Exception):
    pass


class CTFdClient:
    def __init__(self, base_url: str, token: str, verify_tls: bool = True) -> None:
        self.base_url = base_url.rstrip("/") + "/"
        self.session = requests.Session()
        self.session.verify = verify_tls
        self.session.headers.update(
            {"Authorization": f"Token {token}", "Content-Type": "application/json"}
        )

    def request(self, method: str, path: str, **kwargs: Any) -> requests.Response:
        response = self.session.request(
            method, urljoin(self.base_url, path.lstrip("/")), **kwargs
        )
        if not response.ok:
            raise DeployError(f"{method} {path} failed ({response.status_code}): {response.text}")
        return response

    def get_json(self, path: str) -> dict[str, Any]:
        return self.request("GET", path).json()

    def post_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self.request("POST", path, json=payload).json()

    def patch_json(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self.request("PATCH", path, json=payload).json()

    def delete(self, path: str) -> None:
        self.request("DELETE", path)

    def post_files(
        self, path: str, data: dict[str, Any], files: list[tuple[str, tuple[str, Any]]]
    ) -> dict[str, Any]:
        content_type = self.session.headers.pop("Content-Type", None)
        try:
            response = self.request("POST", path, data=data, files=files).json()
        finally:
            if content_type is not None:
                self.session.headers["Content-Type"] = content_type
        if len(response.get("data") or []) != len(files):
            raise DeployError(f"Expected {len(files)} uploaded file(s), got: {response}")
        return response


def load_yaml(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise DeployError(f"challenge config not found: {path}")
    with path.open("r", encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    if not isinstance(data, dict):
        raise DeployError(f"{path} must contain a YAML object")
    return data


def as_int(value: Any, field: str, default: int | None = None) -> int:
    if value is None:
        if default is None:
            raise DeployError(f"Missing required integer field: {field}")
        return default
    try:
        return int(value)
    except (TypeError, ValueError) as exc:
        raise DeployError(f"Field {field} must be an integer") from exc


def first_flag(challenge: dict[str, Any]) -> str | None:
    flags = challenge.get("flags") or []
    if not flags:
        return None
    flag = flags[0]
    if isinstance(flag, str):
        return flag
    if isinstance(flag, dict):
        content = flag.get("content")
        return str(content) if content else None
    raise DeployError("flags entries must be strings or objects")


def parse_container_flag(pattern: str | None) -> dict[str, Any]:
    if not pattern:
        return {}
    random_match = re.search(r"<ran_(\d+)>", pattern)
    if random_match:
        prefix, suffix = pattern.split(random_match.group(0), 1)
        return {
            "flag_mode": "random",
            "flag_prefix": prefix,
            "flag_suffix": suffix,
            "random_flag_length": int(random_match.group(1)),
        }
    return {
        "flag_mode": "static",
        "flag_prefix": pattern,
        "flag_suffix": "",
        "random_flag_length": 0,
    }


def container_flag_delivery_payload(challenge: dict[str, Any]) -> dict[str, Any]:
    mode_value = challenge.get("flag_delivery_mode")
    file_fields = (
        "flag_file_path",
        "flag_file_owner",
        "flag_file_group",
        "flag_file_mode",
    )

    if mode_value is None:
        configured_file_fields = [field for field in file_fields if field in challenge]
        if configured_file_fields:
            raise DeployError(
                "flag_delivery_mode is required when configuring "
                + ", ".join(configured_file_fields)
            )
        return {}

    mode = str(mode_value).strip().lower()
    if mode not in ("environment", "file"):
        raise DeployError("flag_delivery_mode must be 'environment' or 'file'")

    payload = {"flag_delivery_mode": mode}
    if mode == "environment":
        return payload

    path = str(challenge.get("flag_file_path") or "/home/ctf/flag.txt").strip()
    if not path.startswith("/") or ".." in path.split("/"):
        raise DeployError("flag_file_path must be an absolute path without '..'")

    owner = str(challenge.get("flag_file_owner") or "target").strip()
    group = str(challenge.get("flag_file_group") or owner).strip()
    if not owner or not group:
        raise DeployError("flag_file_owner and flag_file_group must not be empty")

    file_mode = str(challenge.get("flag_file_mode") or "440").strip()
    if not re.fullmatch(r"0?[0-7]{3}", file_mode):
        raise DeployError("flag_file_mode must be a three-digit octal mode such as '440'")

    payload.update(
        {
            "flag_file_path": path,
            "flag_file_owner": owner,
            "flag_file_group": group,
            "flag_file_mode": file_mode,
        }
    )
    return payload


def description_with_attachment(challenge: dict[str, Any], ip_server: str | None = None) -> str:
    description = str(challenge.get("description") or "")
    attachment = challenge.get("attachment")
    if attachment and attachment_is_external(attachment):
        description = description.rstrip() + f"\n\nAttachment: [{attachment}]({attachment})"
    return render_template_values(description, challenge, ip_server=ip_server)


def attachment_is_external(attachment: Any) -> bool:
    if isinstance(attachment, str):
        parsed = urlparse(attachment)
        return parsed.scheme in ("http", "https")
    return False


def render_template_values(text: str, challenge: dict[str, Any], ip_server: str | None = None) -> str:
    port = port_for_description(challenge)
    replacements = {
        "{{ IP_SERVER }}": ip_server or "{{ IP_SERVER }}",
        "{{IP_SERVER}}": ip_server or "{{IP_SERVER}}",
        "{{ PORT }}": str(port) if port is not None else "{{ PORT }}",
        "{{PORT}}": str(port) if port is not None else "{{PORT}}",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text


def port_for_description(challenge: dict[str, Any]) -> int | None:
    expose = challenge.get("expose") or {}
    if not isinstance(expose, dict):
        return None
    port = expose.get("targetPort") or expose.get("port")
    if port is None and normalize_challenge_type(challenge.get("type")) != "container":
        port = expose.get("containerPort")
    if port is None:
        return None
    return as_int(port, "expose.targetPort")


def connection_text_for_description(challenge: dict[str, Any]) -> str:
    challenge_type = normalize_challenge_type(challenge.get("type"))
    connection = str(challenge.get("connection") or "").lower()

    if challenge_type == "container":
        if connection == "http":
            return "Start an instance to receive your HTTP URL."
        if connection in ("tcp", "nc"):
            return "Start an instance to receive your TCP host and port."
        return ""

    if connection == "http":
        return "http://{{ IP_SERVER }}:{{ PORT }}/"
    if connection in ("tcp", "nc"):
        return "nc {{ IP_SERVER }} {{ PORT }}"
    return ""


def base_payload(challenge: dict[str, Any], ip_server: str | None = None) -> dict[str, Any]:
    for field in ("name", "category"):
        if not challenge.get(field):
            raise DeployError(f"Missing required field: {field}")
    payload = {
        "name": challenge["name"],
        "category": challenge.get("category", ""),
        "description": description_with_attachment(challenge, ip_server=ip_server),
        "attribution": challenge.get("author", ""),
        "state": "hidden",
        "max_attempts": as_int(challenge.get("attempts"), "attempts", 0),
    }
    connection_info = rendered_connection_info(challenge, ip_server=ip_server)
    if connection_info:
        payload["connection_info"] = connection_info
    return payload


def rendered_connection_info(challenge: dict[str, Any], ip_server: str | None = None) -> str:
    explicit_connection = challenge.get("connection_info")
    if explicit_connection:
        return render_template_values(str(explicit_connection), challenge, ip_server=ip_server)

    connection_text = connection_text_for_description(challenge)
    if not connection_text:
        return ""
    return render_template_values(connection_text, challenge, ip_server=ip_server)


def challenge_payload(challenge: dict[str, Any], ip_server: str | None = None) -> dict[str, Any]:
    challenge_type = challenge.get("type", "standard")
    decay_function = challenge.get("decay-function", challenge.get("function", "logarithmic"))
    payload = base_payload(challenge, ip_server=ip_server)

    if challenge_type == "dynamic":
        value = as_int(challenge.get("value"), "value")
        payload.update(
            {
                "type": "dynamic",
                "value": value,
                "initial": value,
                "decay": as_int(challenge.get("decay"), "decay"),
                "minimum": as_int(challenge.get("minimum"), "minimum"),
                "function": decay_function,
            }
        )
        return payload

    if challenge_type == "container":
        expose = challenge.get("expose") or {}
        if not isinstance(expose, dict):
            raise DeployError("expose must be an object")
        scoring_type = challenge.get("scoring-type", "standard")
        image = os.getenv("CTFD_IMAGE") or challenge.get("image")
        payload.update(
            {
                "type": "container",
                "image": image,
                "internal_port": as_int(expose.get("containerPort"), "expose.containerPort"),
                "connection_type": challenge.get("connection", "tcp"),
                "connection_info": rendered_connection_info(challenge, ip_server=ip_server),
                "timeout_minutes": as_int(challenge.get("alive-time"), "alive-time", 60),
                "command": challenge.get("command", ""),
                "scoring_type": scoring_type,
                "decay_function": decay_function,
            }
        )
        if not payload["image"]:
            raise DeployError("Container challenges require image")
        payload.update(container_flag_delivery_payload(challenge))
        if challenge.get("pids-limit") is not None:
            pids_limit = as_int(challenge.get("pids-limit"), "pids-limit")
            if pids_limit < 1:
                raise DeployError("pids-limit must be greater than zero")
            payload["pids_limit"] = pids_limit
        if scoring_type == "dynamic":
            value = as_int(challenge.get("value"), "value")
            payload.update(
                {
                    "value": value,
                    "initial": value,
                    "decay": as_int(challenge.get("decay"), "decay"),
                    "minimum": as_int(challenge.get("minimum"), "minimum"),
                }
            )
        else:
            payload["value"] = as_int(challenge.get("value"), "value")
        payload.update(parse_container_flag(first_flag(challenge)))
        return payload

    if challenge_type == "static":
        challenge_type = "standard"
    payload.update({"type": challenge_type, "value": as_int(challenge.get("value"), "value")})
    return payload


def normalize_challenge_type(challenge_type: str | None) -> str:
    if challenge_type in (None, "", "static"):
        return "standard"
    return challenge_type


def list_challenges(client: CTFdClient) -> list[dict[str, Any]]:
    return client.get_json("/api/v1/challenges?view=admin").get("data") or []


def find_challenge(client: CTFdClient, name: str) -> dict[str, Any] | None:
    return next((challenge for challenge in list_challenges(client) if challenge.get("name") == name), None)


def delete_challenge(client: CTFdClient, challenge_id: int, name: str) -> None:
    client.delete(f"/api/v1/challenges/{challenge_id}")
    if find_challenge(client, name):
        raise DeployError(f"Challenge {challenge_id} still exists after delete request")


def delete_existing(client: CTFdClient, path: str, challenge_id: int) -> None:
    for row in client.get_json(path).get("data") or []:
        if row.get("challenge_id") == challenge_id:
            client.delete(f"{path}/{row['id']}")


def sync_flags(client: CTFdClient, challenge: dict[str, Any], challenge_id: int) -> None:
    if challenge.get("type") == "container":
        return
    delete_existing(client, "/api/v1/flags", challenge_id)
    for flag in challenge.get("flags") or []:
        if isinstance(flag, str):
            payload = {"challenge_id": challenge_id, "type": "static", "content": flag}
        elif isinstance(flag, dict):
            payload = {**flag, "challenge_id": challenge_id}
        else:
            raise DeployError("flags entries must be strings or objects")
        client.post_json("/api/v1/flags", payload)


def sync_tags(client: CTFdClient, challenge: dict[str, Any], challenge_id: int) -> None:
    delete_existing(client, "/api/v1/tags", challenge_id)
    for tag in challenge.get("tags") or []:
        client.post_json("/api/v1/tags", {"challenge_id": challenge_id, "value": str(tag)})


def sync_hints(client: CTFdClient, challenge: dict[str, Any], challenge_id: int) -> None:
    delete_existing(client, "/api/v1/hints", challenge_id)
    for hint in challenge.get("hints") or []:
        if isinstance(hint, str):
            payload = {"challenge_id": challenge_id, "content": hint, "cost": 0}
        elif isinstance(hint, dict):
            payload = {
                "challenge_id": challenge_id,
                "content": hint.get("content", ""),
                "cost": as_int(hint.get("cost"), "hint.cost", 0),
            }
            if hint.get("title"):
                payload["title"] = hint["title"]
        else:
            raise DeployError("hints entries must be strings or objects")
        client.post_json("/api/v1/hints", payload)


def sync_files(
    client: CTFdClient, challenge: dict[str, Any], challenge_id: int, challenge_dir: Path
) -> None:
    for remote in client.get_json(f"/api/v1/challenges/{challenge_id}/files").get("data") or []:
        client.delete(f"/api/v1/files/{remote['id']}")

    files = []
    handles = []
    try:
        for path in challenge_file_paths(challenge, challenge_dir):
            if not path.is_file():
                raise DeployError(f"Challenge file not found: {path}")
            handle = path.open("rb")
            handles.append(handle)
            files.append(("file", (path.name, handle)))
        if files:
            client.post_files("/api/v1/files", {"challenge_id": challenge_id, "type": "challenge"}, files)
    finally:
        for handle in handles:
            handle.close()


def challenge_file_paths(challenge: dict[str, Any], challenge_dir: Path) -> list[Path]:
    paths = []
    seen = set()
    for file_name in challenge_file_names(challenge):
        path = challenge_dir / file_name
        if path.is_dir():
            expanded_paths = sorted(child for child in path.rglob("*") if child.is_file())
            if not expanded_paths:
                raise DeployError(f"Directory contains no files: {path}")
        else:
            expanded_paths = [path]

        for expanded_path in expanded_paths:
            resolved = expanded_path.resolve()
            if resolved not in seen:
                seen.add(resolved)
                paths.append(expanded_path)
    return paths


def challenge_file_names(challenge: dict[str, Any]) -> list[str]:
    file_names = list(challenge.get("files") or [])
    attachment = challenge.get("attachment")
    if isinstance(attachment, str) and attachment and not attachment_is_external(attachment):
        file_names.append(attachment)
    elif isinstance(attachment, list):
        for item in attachment:
            if isinstance(item, str) and item and not attachment_is_external(item):
                file_names.append(item)
            elif item:
                raise DeployError("attachment list entries must be local file paths or URLs")
    elif attachment and not isinstance(attachment, str):
        raise DeployError("attachment must be a string path, URL, or list of paths/URLs")

    deduped = []
    seen = set()
    for file_name in file_names:
        if not isinstance(file_name, str):
            raise DeployError("files entries must be strings")
        if file_name not in seen:
            seen.add(file_name)
            deduped.append(file_name)
    return deduped


def deploy(
    client: CTFdClient,
    challenge_path: Path,
    dry_run: bool = False,
    recreate_type_changes: bool = True,
    ip_server: str | None = None,
) -> int | None:
    challenge = load_yaml(challenge_path)
    payload = challenge_payload(challenge, ip_server=ip_server)
    if dry_run:
        print(json.dumps(payload, indent=2, sort_keys=True))
        return None

    existing = find_challenge(client, payload["name"])
    if existing:
        challenge_id = int(existing["id"])
        existing_type = normalize_challenge_type(existing.get("type"))
        desired_type = normalize_challenge_type(payload.get("type"))
        existing_state = existing.get("state", "hidden")
        if existing_type != desired_type:
            if not recreate_type_changes:
                raise DeployError(
                    f"Existing challenge {challenge_id} has type {existing_type!r}, "
                    f"but YAML wants {desired_type!r}"
                )
            delete_challenge(client, challenge_id, payload["name"])
            challenge_id = int(client.post_json("/api/v1/challenges", payload)["data"]["id"])
            action = f"recreated ({existing_type} -> {desired_type})"
        else:
            client.patch_json(f"/api/v1/challenges/{challenge_id}", payload)
            action = "updated"
    else:
        challenge_id = int(client.post_json("/api/v1/challenges", payload)["data"]["id"])
        action = "created"
        existing_state = None

    sync_flags(client, challenge, challenge_id)
    sync_tags(client, challenge, challenge_id)
    sync_hints(client, challenge, challenge_id)
    sync_files(client, challenge, challenge_id, challenge_path.parent)

    # Retain existing visibility for updates; only new challenges use the YAML state
    if existing_state is not None:
        final_state = existing_state
    else:
        final_state = challenge.get("state", "hidden")
    if final_state not in ("visible", "hidden", "locked"):
        raise DeployError("state must be one of: visible, hidden, locked")
    client.patch_json(f"/api/v1/challenges/{challenge_id}", {"state": final_state})
    print(f"{action} challenge {challenge_id}: {payload['name']} ({payload['type']}, state={final_state})")
    return challenge_id


def config_path_for_environment(challenge_dir: Path, environment: str) -> Path | None:
    """Return the config selected for an environment.

    Local uses challenge.yml exclusively. Mirror prefers challenge-mirror.yml,
    and falls back to challenge.yml when the mirror-specific config is absent.
    """
    local_config = challenge_dir / "challenge.yml"
    mirror_config = challenge_dir / "challenge-mirror.yml"

    if environment == "local":
        return local_config if local_config.is_file() else None
    if environment == "mirror":
        if mirror_config.is_file():
            return mirror_config
        return local_config if local_config.is_file() else None
    raise DeployError(f"Unsupported environment: {environment}")


def challenge_directories(target: Path) -> list[Path]:
    if target.is_file():
        if target.name not in ("challenge.yml", "challenge-mirror.yml"):
            raise DeployError(f"Expected a challenge.yml or challenge-mirror.yml file, got: {target}")
        return [target.parent]
    if target.is_dir():
        config_paths = list(target.rglob("challenge.yml")) + list(target.rglob("challenge-mirror.yml"))
        return sorted({path.parent for path in config_paths if path.is_file()})
    raise DeployError(f"Path not found: {target}")


def discover_challenges(target: Path, environment: str = "local") -> list[Path]:
    return [
        config_path
        for challenge_dir in challenge_directories(target)
        if (config_path := config_path_for_environment(challenge_dir, environment)) is not None
    ]


def deploy_all(
    client: CTFdClient,
    target: Path,
    dry_run: bool = False,
    recreate_type_changes: bool = True,
    ip_server: str | None = None,
    environment: str = "local",
) -> int:
    challenge_paths = discover_challenges(target, environment=environment)
    if not challenge_paths:
        raise DeployError(f"No challenge config files for {environment!r} found under: {target}")

    failures = 0
    for index, challenge_path in enumerate(challenge_paths, start=1):
        print(f"[{index}/{len(challenge_paths)}] {challenge_path}")
        try:
            deploy(
                client,
                challenge_path,
                dry_run=dry_run,
                recreate_type_changes=recreate_type_changes,
                ip_server=ip_server,
            )
        except DeployError as exc:
            failures += 1
            print(f"error: {exc}", file=sys.stderr)

    if failures:
        raise DeployError(f"{failures} of {len(challenge_paths)} deployments failed")
    return len(challenge_paths)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Deploy TC-style challenge configs to CTFd")
    parser.add_argument("target", type=Path, help="Path to a challenge.yml file or a directory to scan recursively")
    parser.add_argument(
        "--environment",
        choices=("local", "mirror"),
        default="local",
        help="Deployment environment; local uses challenge.yml, mirror prefers challenge-mirror.yml",
    )
    parser.add_argument("--url", default=os.getenv("CTFD_URL", "http://localhost:8000"), help="CTFd base URL")
    parser.add_argument("--token", default=os.getenv("CTFD_TOKEN") or os.getenv("CTFCLI_ACCESS_TOKEN"), help="Admin API token")
    parser.add_argument(
        "--ip-server",
        default=os.getenv("IP_SERVER") or os.getenv("CTFD_CHALLENGE_HOST"),
        help="Value used to replace {{ IP_SERVER }} in descriptions",
    )
    parser.add_argument("--insecure", action="store_true", help="Disable TLS verification")
    parser.add_argument("--dry-run", action="store_true", help="Print translated payload and exit")
    parser.add_argument(
        "--no-recreate-type-changes",
        action="store_true",
        help="Fail instead of recreating a same-name challenge whose type changed",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if not args.token and not args.dry_run:
        print("Missing token. Set CTFD_TOKEN or pass --token.", file=sys.stderr)
        return 2
    client = CTFdClient(args.url, args.token or "dry-run", verify_tls=not args.insecure)
    try:
        count = deploy_all(
            client,
            args.target.resolve(),
            dry_run=args.dry_run,
            recreate_type_changes=not args.no_recreate_type_changes,
            ip_server=args.ip_server,
            environment=args.environment,
        )
    except DeployError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    if args.dry_run:
        print(f"translated {count} challenge(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
