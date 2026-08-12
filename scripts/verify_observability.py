#!/usr/bin/env python3
"""查询 Prometheus、Grafana 和 Tempo，并保存可复核的摘要证据。"""

from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import json
from pathlib import Path
import time
from typing import Any
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import ProxyHandler, Request, build_opener


OPENER = build_opener(ProxyHandler({}))


def get_json(url: str, authorization: str | None = None) -> Any:
    headers = {"Accept": "application/json"}
    if authorization:
        headers["Authorization"] = authorization
    with OPENER.open(Request(url, headers=headers), timeout=15) as response:
        return json.load(response)


def wait_for_trace(url: str, authorization: str | None, timeout: float) -> Any:
    deadline = time.monotonic() + timeout
    last_error: HTTPError | None = None
    while time.monotonic() < deadline:
        try:
            return get_json(url, authorization)
        except HTTPError as error:
            if error.code != 404:
                raise
            last_error = error
        time.sleep(1)
    raise RuntimeError(f"trace was not queryable before timeout: {last_error}")


def span_count(trace: dict[str, Any]) -> int:
    return sum(
        len(scope.get("spans", []))
        for batch in trace.get("batches", [])
        for scope in batch.get("scopeSpans", [])
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--task-id", required=True)
    parser.add_argument("--backend-url", default="http://127.0.0.1:8080")
    parser.add_argument("--prometheus-url", default="http://127.0.0.1:9090")
    parser.add_argument("--tempo-url", default="http://127.0.0.1:3200")
    parser.add_argument("--grafana-url", default="http://127.0.0.1:3001")
    parser.add_argument("--grafana-user", default="admin")
    parser.add_argument("--grafana-password", default="opsmind")
    parser.add_argument("--trace-timeout", type=float, default=30)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    basic = base64.b64encode(
        f"{args.grafana_user}:{args.grafana_password}".encode("utf-8")
    ).decode("ascii")
    authorization = f"Basic {basic}"

    task_envelope = get_json(
        f"{args.backend_url.rstrip('/')}/api/diagnosis-tasks/{args.task_id}"
    )
    task = task_envelope.get("data", {})
    if task.get("status") != "SUCCESS" or not task.get("traceId"):
        raise RuntimeError(f"task must be SUCCESS with traceId: {task!r}")
    trace_id = task["traceId"]

    targets = get_json(f"{args.prometheus_url.rstrip('/')}/api/v1/targets")
    backend_targets = [
        target
        for target in targets.get("data", {}).get("activeTargets", [])
        if target.get("labels", {}).get("job") == "opsmind-backend"
    ]
    if not backend_targets or any(target.get("health") != "up" for target in backend_targets):
        raise RuntimeError(f"Prometheus backend target is not up: {backend_targets!r}")

    query = "opsmind_diagnosis_tasks_total"
    encoded_query = quote(query, safe="")
    prometheus_query = get_json(
        f"{args.prometheus_url.rstrip('/')}/api/v1/query?query={encoded_query}"
    )
    series = prometheus_query.get("data", {}).get("result", [])
    if not series:
        raise RuntimeError("Prometheus business metric query returned no series")

    tempo_trace = wait_for_trace(
        f"{args.tempo_url.rstrip('/')}/api/traces/{trace_id}",
        None,
        args.trace_timeout,
    )
    direct_span_count = span_count(tempo_trace)
    if direct_span_count < 2:
        raise RuntimeError(f"Tempo trace contains too few spans: {direct_span_count}")

    grafana_health = get_json(
        f"{args.grafana_url.rstrip('/')}/api/health",
        authorization,
    )
    datasources = get_json(
        f"{args.grafana_url.rstrip('/')}/api/datasources",
        authorization,
    )
    datasource_uids = {item.get("uid") for item in datasources}
    required_uids = {"opsmind-prometheus", "opsmind-tempo"}
    if not required_uids.issubset(datasource_uids):
        raise RuntimeError(f"Grafana datasources missing: {required_uids - datasource_uids}")
    dashboard = get_json(
        f"{args.grafana_url.rstrip('/')}/api/dashboards/uid/opsmind-overview",
        authorization,
    )
    if dashboard.get("dashboard", {}).get("uid") != "opsmind-overview":
        raise RuntimeError("Grafana OpsMind dashboard is not provisioned")

    grafana_prometheus = get_json(
        f"{args.grafana_url.rstrip('/')}/api/datasources/proxy/uid/"
        f"opsmind-prometheus/api/v1/query?query={encoded_query}",
        authorization,
    )
    grafana_series = grafana_prometheus.get("data", {}).get("result", [])
    if not grafana_series:
        raise RuntimeError("Grafana Prometheus proxy query returned no series")
    grafana_tempo = wait_for_trace(
        f"{args.grafana_url.rstrip('/')}/api/datasources/proxy/uid/"
        f"opsmind-tempo/api/traces/{trace_id}",
        authorization,
        args.trace_timeout,
    )
    grafana_span_count = span_count(grafana_tempo)
    if grafana_span_count != direct_span_count:
        raise RuntimeError("Grafana Tempo proxy returned a different trace")

    result = {
        "verifiedAt": datetime.now(timezone.utc).isoformat(),
        "taskId": args.task_id,
        "traceId": trace_id,
        "prometheus": {
            "backendTargetHealth": [item.get("health") for item in backend_targets],
            "businessMetricSeries": [
                {
                    "status": item.get("metric", {}).get("status"),
                    "value": item.get("value", [None, None])[1],
                }
                for item in series
            ],
        },
        "tempo": {
            "batchCount": len(tempo_trace.get("batches", [])),
            "spanCount": direct_span_count,
        },
        "grafana": {
            "database": grafana_health.get("database"),
            "version": grafana_health.get("version"),
            "datasourceUids": sorted(datasource_uids),
            "dashboardUid": dashboard.get("dashboard", {}).get("uid"),
            "prometheusSeriesCount": len(grafana_series),
            "tempoSpanCount": grafana_span_count,
        },
    }
    serialized = json.dumps(result, ensure_ascii=False, indent=2)
    print(serialized)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
