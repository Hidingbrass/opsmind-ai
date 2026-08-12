#!/usr/bin/env python3
"""Check a running OpsMind stack, then delegate to the existing evaluation."""

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener


PROJECT_ROOT = Path(__file__).resolve().parents[1]
EVALUATION_SCRIPT = PROJECT_ROOT / "evaluation" / "run_evaluation.py"
VENV_PYTHON = PROJECT_ROOT / "ai-agent-service" / ".venv" / "bin" / "python"


class HealthCheckError(RuntimeError):
    """Raised when a service health endpoint is unavailable or unhealthy."""


def positive_float(value: str) -> float:
    """Parse a strictly positive timeout value for argparse."""
    parsed = float(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("timeout must be greater than zero")
    return parsed


def endpoint(base_url: str, path: str) -> str:
    """Join a base URL and absolute endpoint path without duplicate slashes."""
    return f"{base_url.rstrip('/')}{path}"


def fetch_json(url: str, timeout_seconds: float) -> dict[str, Any]:
    """Fetch JSON directly, without inheriting shell proxy configuration."""
    request = Request(url, headers={"Accept": "application/json"})
    opener = build_opener(ProxyHandler({}))
    try:
        with opener.open(request, timeout=timeout_seconds) as response:
            payload = json.load(response)
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise HealthCheckError(f"{url}: {exc}") from exc

    if not isinstance(payload, dict):
        raise HealthCheckError(f"{url}: expected a JSON object")
    return payload


def check_health(label: str, url: str, timeout_seconds: float) -> None:
    """Require the service health contract to report status UP."""
    print(f"[health] checking {label}: {url}", flush=True)
    payload = fetch_json(url, timeout_seconds)
    if payload.get("status") != "UP":
        raise HealthCheckError(
            f"{url}: expected status UP, got {payload.get('status')!r}"
        )
    print(f"[health] {label} is UP", flush=True)


def run_evaluation(
    base_url: str,
    timeout_seconds: float,
    output_dir: Path,
) -> int:
    """Run the existing three-scenario evaluator and preserve its exit code."""
    python_bin = str(VENV_PYTHON) if VENV_PYTHON.is_file() else sys.executable
    command = [
        python_bin,
        str(EVALUATION_SCRIPT),
        "--base-url",
        base_url,
        "--timeout",
        str(timeout_seconds),
        "--output",
        str(output_dir),
    ]
    print("[evaluation] running existing three-scenario evaluator", flush=True)
    completed = subprocess.run(command, cwd=PROJECT_ROOT, check=False)
    return completed.returncode


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Smoke-test a running OpsMind stack and run its evaluation"
    )
    parser.add_argument(
        "--base-url",
        default="http://127.0.0.1:8080",
        help="Spring Boot base URL (default: %(default)s)",
    )
    parser.add_argument(
        "--ai-base-url",
        default="http://127.0.0.1:8000",
        help="FastAPI base URL (default: %(default)s)",
    )
    parser.add_argument(
        "--timeout",
        type=positive_float,
        default=180.0,
        help="Per-task evaluation timeout in seconds (default: %(default)s)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Persist evaluation reports here; defaults to a temporary directory",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    health_timeout = min(args.timeout, 15.0)

    try:
        check_health(
            "Spring Boot",
            endpoint(args.base_url, "/actuator/health"),
            health_timeout,
        )
        check_health(
            "FastAPI",
            endpoint(args.ai_base_url, "/ai/health"),
            health_timeout,
        )
    except HealthCheckError as exc:
        print(f"[health] FAIL: {exc}", file=sys.stderr)
        return 1

    if args.output is not None:
        return run_evaluation(args.base_url, args.timeout, args.output)

    with tempfile.TemporaryDirectory(prefix="opsmind-smoke-") as temp_dir:
        print(f"[evaluation] temporary reports: {temp_dir}", flush=True)
        return run_evaluation(args.base_url, args.timeout, Path(temp_dir))


if __name__ == "__main__":
    raise SystemExit(main())
