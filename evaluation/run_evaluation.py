"""通过真实 HTTP 闭环运行三场景诊断评测并输出 JSON/Markdown 报告。"""

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx


ROOT = Path(__file__).resolve().parent
DEFAULT_DATASET = ROOT / "dataset.json"
DEFAULT_OUTPUT = ROOT / "results"
TERMINAL_STATUSES = {"SUCCESS", "FAILED"}


def api_request(
    client: httpx.Client,
    method: str,
    path: str,
    json_body: dict[str, Any] | None = None,
) -> Any:
    """调用 Spring 统一 Result 接口并提取 data，失败时保留服务端消息。"""
    response = client.request(method, path, json=json_body)
    response.raise_for_status()
    body = response.json()
    if body.get("code") != 0:
        raise RuntimeError(body.get("message", "OpsMind API 调用失败"))
    return body.get("data")


def wait_for_task(
    client: httpx.Client,
    task_id: str,
    timeout_seconds: float,
) -> dict[str, Any]:
    """轮询异步任务直到终态，超时则报告当前 taskId。"""
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        task = api_request(client, "GET", f"/api/diagnosis-tasks/{task_id}")
        if task["status"] in TERMINAL_STATUSES:
            return task
        time.sleep(0.5)
    raise TimeoutError(f"诊断任务等待超时: {task_id}")


def contains_all(value: str, keywords: list[str]) -> bool:
    """忽略大小写检查期望关键词是否全部出现在目标文本中。"""
    normalized = value.lower()
    return all(keyword.lower() in normalized for keyword in keywords)


def score_mark(value: Any) -> str:
    """把布尔评分转换成 Markdown 表格中易读的 PASS 或 FAIL。"""
    return "PASS" if value else "FAIL"


def evaluate_case(
    client: httpx.Client,
    case: dict[str, Any],
    timeout_seconds: float,
) -> dict[str, Any]:
    """注入故障、运行异步诊断，并按根因、证据和建议三个维度评分。"""
    started_at = time.monotonic()
    injected = api_request(
        client,
        "POST",
        f"/api/fault-scenarios/{case['scenarioKey']}/inject",
    )
    incident = injected["incident"]
    task = api_request(
        client,
        "POST",
        f"/api/diagnosis-tasks/incidents/{incident['id']}",
    )
    task = wait_for_task(client, task["id"], timeout_seconds)

    if task["status"] != "SUCCESS":
        return {
            "scenarioKey": case["scenarioKey"],
            "passed": False,
            "taskId": task["id"],
            "failureReason": task.get("failureReason"),
            "latencyMs": round((time.monotonic() - started_at) * 1000),
        }

    records = api_request(
        client,
        "GET",
        f"/api/diagnoses/incidents/{incident['id']}/records",
    )
    incident_report = api_request(
        client,
        "POST",
        "/api/tools/execute",
        {
            "taskId": task["id"],
            "incidentId": incident["id"],
            "toolName": "generateIncidentReport",
            "arguments": {"incidentId": incident["id"]},
        },
    )
    audits = api_request(
        client,
        "GET",
        f"/api/tool-call-audits?taskId={task['id']}",
    )
    report = records[0]
    evidence_text = " ".join(report["evidence"])

    scores = {
        "rootCause": contains_all(
            report["rootCause"],
            case["expectedRootCauseKeywords"],
        ),
        "evidence": contains_all(
            evidence_text,
            case["expectedEvidenceKeywords"],
        ),
        "recommendation": contains_all(
            report["recommendation"],
            case["expectedRecommendationKeywords"],
        ),
        "toolCoverage": {
            audit["toolName"]
            for audit in audits
            if audit["status"] == "SUCCESS"
        }.issuperset({
            "queryLogs",
            "queryMetrics",
            "queryTrace",
            "searchRunbook",
            "getRecentDeployments",
            "generateIncidentReport",
        }),
        "incidentReport": (
            incident_report["status"] == "SUCCESS"
            and incident_report["data"]["rootCause"] == report["rootCause"]
        ),
    }

    return {
        "scenarioKey": case["scenarioKey"],
        "passed": all(scores.values()),
        "scores": scores,
        "taskId": task["id"],
        "incidentId": incident["id"],
        "diagnosisRecordId": task["diagnosisRecordId"],
        "latencyMs": round((time.monotonic() - started_at) * 1000),
        "confidence": report["confidence"],
        "toolCalls": [audit["toolName"] for audit in reversed(audits)],
    }


def write_reports(results: list[dict[str, Any]], output_dir: Path) -> None:
    """同时输出机器可读 JSON 和便于作品展示的 Markdown 报告。"""
    output_dir.mkdir(parents=True, exist_ok=True)
    passed = sum(1 for result in results if result["passed"])
    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "passed": passed,
        "total": len(results),
        "passRate": passed / len(results) if results else 0,
        "results": results,
    }
    (output_dir / "report.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    lines = [
        "# OpsMind AI 诊断评测报告",
        "",
        f"- 生成时间：{summary['generatedAt']}",
        f"- 通过：{passed}/{len(results)}",
        f"- 通过率：{summary['passRate']:.0%}",
        "",
        "| 场景 | 结果 | 根因 | 证据 | 建议 | 工具覆盖 | 复盘 | 置信度 | 延迟 |",
        "| --- | --- | --- | --- | --- | --- | --- | ---: | ---: |",
    ]
    for result in results:
        status = "PASS" if result["passed"] else "FAIL"
        scores = result.get("scores", {})
        lines.append(
            f"| {result['scenarioKey']} | {status} | "
            f"{score_mark(scores.get('rootCause'))} | "
            f"{score_mark(scores.get('evidence'))} | "
            f"{score_mark(scores.get('recommendation'))} | "
            f"{score_mark(scores.get('toolCoverage'))} | "
            f"{score_mark(scores.get('incidentReport'))} | "
            f"{result.get('confidence', 0):.2f} | "
            f"{result['latencyMs']}ms |"
        )
    lines.extend([
        "",
        "## 工具调用",
        "",
    ])
    for result in results:
        calls = " -> ".join(result.get("toolCalls", [])) or "无"
        lines.append(f"- `{result['scenarioKey']}`：{calls}")
    lines.extend([
        "",
        "> 首个场景延迟包含中文向量模型冷启动；后续场景复用进程内模型缓存。",
    ])
    (output_dir / "report.md").write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="运行 OpsMind 三场景诊断评测")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--timeout", type=float, default=180)
    args = parser.parse_args()

    cases = json.loads(args.dataset.read_text(encoding="utf-8"))
    # 评测目标通常是本机或容器映射端口，不应继承系统 HTTP 代理。
    with httpx.Client(
        base_url=args.base_url,
        timeout=15,
        trust_env=False,
    ) as client:
        results = [
            evaluate_case(client, case, args.timeout)
            for case in cases
        ]
    write_reports(results, args.output)
    print(json.dumps(results, ensure_ascii=False, indent=2))

    if not all(result["passed"] for result in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
