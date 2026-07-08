from app.schemas import DiagnosisReport, DiagnosisRequest
from app.rag.runbook_search import search_runbooks


def generate_diagnosis(request: DiagnosisRequest) -> DiagnosisReport:
    evidence = []

    runbook_query = (
        f"{request.incident.title} "
        f"{request.incident.serviceName} "
        f"{request.incident.symptom}"
    )
    runbook_hits = search_runbooks(runbook_query, n_results=2)

    has_timeout_log = any(
        "timeout" in log.message.lower() or "超时" in log.message
        for log in request.logs
    )

    has_high_latency_metric = any(
        metric.metricName.endswith("p95") and metric.value >= 3000
        for metric in request.metrics
    )

    has_error_trace = any(
        span.status == "ERROR"
        for span in request.traces
    )

    if has_timeout_log:
        evidence.append("日志中发现支付链路超时错误")
    if has_high_latency_metric:
        evidence.append("指标显示服务 P95 延迟超过 3000ms")
    if has_error_trace:
        evidence.append("链路追踪显示存在 ERROR 调用节点")
    if runbook_hits:
        first_hit = runbook_hits[0]
        metadata = first_hit.get("metadata", {})
        source = metadata.get("source", "unknown")
        evidence.append(f"知识库命中相关 Runbook: {source}")

    recommendation = "建议检查第三方支付网关状态，必要时切换备用支付通道或启用支付降级策略。"

    if runbook_hits:
        recommendation = (
            recommendation
            + " 知识库建议参考相关 Runbook，优先按排查步骤确认日志、P95/P99 延迟、链路追踪和备用支付通道状态。"
        )

    return DiagnosisReport(
        incidentId=request.incident.id,
        summary=f"检测到 {request.incident.serviceName} 存在异常，故障与支付链路超时高度相关。",
        rootCause="支付服务调用第三方支付网关超时，导致订单结算请求失败。",
        evidence=evidence,
        recommendation=recommendation,
        confidence=0.86,
    )
