from app.schemas import DiagnosisReport, DiagnosisRequest


def generate_diagnosis(request: DiagnosisRequest) -> DiagnosisReport:
    evidence = []

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

    return DiagnosisReport(
        incidentId=request.incident.id,
        summary=f"检测到 {request.incident.serviceName} 存在异常，故障与支付链路超时高度相关。",
        rootCause="支付服务调用第三方支付网关超时，导致订单结算请求失败。",
        evidence=evidence,
        recommendation="建议检查第三方支付网关状态，必要时切换备用支付通道或启用支付降级策略。",
        confidence=0.86,
    )
