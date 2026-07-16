"""Use observability signals and Runbook RAG hits to build a structured diagnosis."""

from app.schemas import DiagnosisReport, DiagnosisRequest
from app.rag.runbook_search import search_runbooks


def generate_diagnosis(request: DiagnosisRequest) -> DiagnosisReport:
    """Diagnose one incident and return the first matching scenario report.

    The MVP uses deterministic rules so the three demo incidents remain repeatable.
    Runbook retrieval augments each branch with a knowledge-base source.
    """
    # evidence 只收集当前分支实际命中的信号，避免报告声称不存在的证据。
    evidence = []

    # 用事故标题、服务名和故障现象作为检索词，让 RAG 找到最相关的 Runbook。
    runbook_query = (
        f"{request.incident.title} "
        f"{request.incident.serviceName} "
        f"{request.incident.symptom}"
    )
    runbook_hits = search_runbooks(runbook_query, n_results=2)

    # 把日志、指标名和 Trace 文本合并成统一信号，后面按关键词判断故障类型。
    all_log_text = " ".join(log.message for log in request.logs)
    all_metric_names = " ".join(metric.metricName for metric in request.metrics)
    all_trace_text = " ".join(
        f"{span.serviceName} {span.operationName} {span.status} {span.errorMessage or ''}"
        for span in request.traces
    )

    # signal_text 是用于场景分类的统一可搜索文本。
    signal_text = (
        f"{request.incident.title} "
        f"{request.incident.serviceName} "
        f"{request.incident.symptom} "
        f"{all_log_text} "
        f"{all_metric_names} "
        f"{all_trace_text}"
    )

    # 具体场景标志要在通用支付超时逻辑之前判断。
    is_redis_issue = contains_any(
        signal_text,
        ["redis", "connection refused", "连接池", "缓存", "cache-service"]
    )

    is_db_issue = contains_any(
        signal_text,
        ["slow query", "数据库慢查询", "full table scan", "db.query", "mysql", "索引"]
    )
    # 这些是通用证据判断，Redis、数据库慢查询和支付超时分支都会用到其中一部分。
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

    # Redis 故障要优先判断，避免 Redis timeout 被后面的支付超时逻辑误判。
    if is_redis_issue:
        evidence.append("日志或链路追踪中发现 Redis 连接失败")

        if "redis.connection.errors" in all_metric_names.lower():
            evidence.append("指标显示 Redis 连接错误数升高")

        if has_error_trace:
            evidence.append("链路追踪显示 Redis 调用节点异常")

        if runbook_hits:
            first_hit = runbook_hits[0]
            metadata = first_hit.get("metadata", {})
            source = metadata.get("source", "unknown")
            evidence.append(f"知识库命中相关 Runbook: {source}")

        return DiagnosisReport(
            incidentId=request.incident.id,
            summary="检测到 cache-service 存在 Redis 连接异常，缓存能力受损。",
            rootCause="Redis 实例不可达或连接池耗尽，导致缓存读取失败。",
            evidence=evidence,
            recommendation="建议检查 Redis 实例状态、网络连通性和连接池配置，必要时启用缓存降级。",
            confidence=0.84,
        )

    # 数据库慢查询比支付超时更具体，也要放在兜底的支付逻辑之前。
    if is_db_issue:
        evidence.append("日志或链路追踪中发现数据库慢查询")

        if "db.query.latency.p95" in all_metric_names.lower():
            evidence.append("指标显示数据库查询 P95 延迟升高")

        if has_error_trace:
            evidence.append("链路追踪显示数据库查询节点异常或耗时过高")

        if runbook_hits:
            first_hit = runbook_hits[0]
            metadata = first_hit.get("metadata", {})
            source = metadata.get("source", "unknown")
            evidence.append(f"知识库命中相关 Runbook: {source}")

        return DiagnosisReport(
            incidentId=request.incident.id,
            summary=f"检测到 {request.incident.serviceName} 存在数据库慢查询，接口延迟明显升高。",
            rootCause="订单查询 SQL 缺少合适索引或扫描行数过多，导致数据库查询耗时升高。",
            evidence=evidence,
            recommendation="建议定位慢 SQL，使用 explain 检查索引命中情况，并评估补充组合索引或优化分页策略。",
            confidence=0.85,
        )

    # 走到这里说明没有识别为 Redis 场景，继续使用原来的支付超时诊断规则。
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


def contains_any(value: str, keywords: list[str]) -> bool:
    """Return True when value contains at least one case-insensitive keyword."""
    # 统一转小写后匹配，避免 Redis、redis、REDIS 这种大小写差异影响判断。
    lower_value = value.lower()
    return any(keyword.lower() in lower_value for keyword in keywords)
