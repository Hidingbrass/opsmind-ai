#!/usr/bin/env python3
"""验证同任务双 SSE 订阅和后端进程中断后的任务对账。"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener


TERMINAL_STATUSES = {"SUCCESS", "FAILED"}
OPENER = build_opener(ProxyHandler({}))


def request_json(
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    timeout: float = 30,
) -> Any:
    request = Request(
        f"{base_url.rstrip('/')}{path}",
        method=method,
        headers={"Accept": "application/json", "Content-Type": "application/json"},
        data=b"" if method == "POST" else None,
    )
    with OPENER.open(request, timeout=timeout) as response:
        body = json.load(response)
    if not isinstance(body, dict) or body.get("code") != 0:
        raise RuntimeError(f"unexpected API response for {path}: {body!r}")
    return body.get("data")


def inject_incident(base_url: str, scenario: str) -> str:
    result = request_json(
        base_url,
        f"/api/fault-scenarios/{scenario}/inject",
        method="POST",
    )
    return result["incident"]["id"]


def create_task(base_url: str, incident_id: str) -> str:
    task = request_json(
        base_url,
        f"/api/diagnosis-tasks/incidents/{incident_id}",
        method="POST",
    )
    return task["id"]


def read_sse(base_url: str, task_id: str, timeout: float) -> dict[str, Any]:
    request = Request(
        f"{base_url.rstrip('/')}/api/diagnosis-tasks/{task_id}/events",
        headers={"Accept": "text/event-stream"},
    )
    events: list[dict[str, Any]] = []
    current_event = "message"
    with OPENER.open(request, timeout=timeout) as response:
        for raw_line in response:
            line = raw_line.decode("utf-8").strip()
            if line.startswith("event:"):
                current_event = line.removeprefix("event:").strip()
            elif line.startswith("data:"):
                payload = json.loads(line.removeprefix("data:").strip())
                events.append({"event": current_event, "payload": payload})
                if payload.get("status") in TERMINAL_STATUSES:
                    break
    if not events or events[-1]["payload"].get("status") not in TERMINAL_STATUSES:
        raise RuntimeError(f"SSE subscriber did not receive terminal state: {events!r}")
    return {
        "stages": [item["payload"].get("stage") for item in events],
        "terminalStatus": events[-1]["payload"]["status"],
        "eventCount": len(events),
    }


def verify_double_sse(base_url: str, timeout: float) -> dict[str, Any]:
    incident_id = inject_incident(base_url, "payment-timeout")
    task_id = create_task(base_url, incident_id)
    with ThreadPoolExecutor(max_workers=2, thread_name_prefix="sse-check-") as pool:
        futures = [
            pool.submit(read_sse, base_url, task_id, timeout)
            for _ in range(2)
        ]
        subscribers = [future.result(timeout=timeout + 5) for future in futures]
    statuses = {item["terminalStatus"] for item in subscribers}
    if statuses != {"SUCCESS"}:
        raise RuntimeError(f"double SSE task did not succeed: {subscribers!r}")
    return {
        "incidentId": incident_id,
        "taskId": task_id,
        "subscribers": subscribers,
    }


def docker(*arguments: str) -> None:
    subprocess.run(["docker", *arguments], check=True)


def wait_until_healthy(base_url: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            request = Request(f"{base_url.rstrip('/')}/actuator/health")
            with OPENER.open(request, timeout=3) as response:
                payload = json.load(response)
            if payload.get("status") == "UP":
                return
        except (
            HTTPError,
            URLError,
            TimeoutError,
            OSError,
            json.JSONDecodeError,
        ) as error:
            last_error = error
        time.sleep(1)
    raise RuntimeError(f"backend did not become healthy: {last_error}")


def verify_process_recovery(
    base_url: str,
    container_name: str,
    timeout: float,
) -> dict[str, Any]:
    incident_id = inject_incident(base_url, "database-slow-query")
    task_id = create_task(base_url, incident_id)
    docker("kill", container_name)
    docker("start", container_name)
    wait_until_healthy(base_url, timeout)
    task = request_json(base_url, f"/api/diagnosis-tasks/{task_id}")
    if task.get("status") != "FAILED" or "服务重启" not in (task.get("failureReason") or ""):
        raise RuntimeError(f"interrupted task was not reconciled as FAILED: {task!r}")
    return {
        "incidentId": incident_id,
        "taskId": task_id,
        "status": task["status"],
        "failureReason": task["failureReason"],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--backend-container", default="opsmind-backend")
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    result = {
        "verifiedAt": datetime.now(timezone.utc).isoformat(),
        "baseUrl": args.base_url,
        "doubleSse": verify_double_sse(args.base_url, args.timeout),
        "processRecovery": verify_process_recovery(
            args.base_url,
            args.backend_container,
            args.timeout,
        ),
    }
    serialized = json.dumps(result, ensure_ascii=False, indent=2)
    print(serialized)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
