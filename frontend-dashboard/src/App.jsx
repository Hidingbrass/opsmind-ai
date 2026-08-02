import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Activity,
  AlertTriangle,
  BookOpenText,
  CheckCircle2,
  ChevronRight,
  CircleGauge,
  Clock3,
  Database,
  FileSearch,
  Gauge,
  GitCommitHorizontal,
  ListChecks,
  Play,
  RefreshCw,
  ServerCog,
  ShieldCheck,
  TerminalSquare,
  XCircle,
  Zap,
} from "lucide-react";

import { api } from "./api";

const EVENT_NAMES = [
  "PENDING",
  "RUNNING",
  "CALL_AI",
  "TOOL_CALL",
  "TOOL_SUCCESS",
  "TOOL_FAILED",
  "SUCCESS",
  "FAILED",
];

/** 故障列表按创建时间倒序，确保刚注入或刚发生的事件始终最先被看到。 */
function compareIncidents(left, right) {
  return new Date(right.createdAt) - new Date(left.createdAt);
}

/** 把后端 ISO 时间转换成适合控制台扫描的本地月日和时分秒。 */
function formatTime(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
}

/** 根据后端枚举值选择统一的状态样式。 */
function StatusBadge({ status }) {
  const normalized = status || "UNKNOWN";
  return <span className={`status status-${normalized.toLowerCase()}`}>{normalized}</span>;
}

/** 只有外部模型模式才展示 Token；确定性模式的 0 不是模型用量。 */
function formatAgentUsage(metadata) {
  if (!metadata || metadata.executionMode !== "LLM") return "无外部模型 Token";
  return `${metadata.inputTokens + metadata.outputTokens} tokens · ${metadata.toolCallCount} tools`;
}

/** 为 SSE 时间线中的不同阶段选择语义图标。 */
function StageIcon({ stage }) {
  if (stage === "SUCCESS" || stage === "TOOL_SUCCESS") return <CheckCircle2 size={16} />;
  if (stage === "FAILED" || stage === "TOOL_FAILED") return <XCircle size={16} />;
  if (stage === "TOOL_CALL") return <TerminalSquare size={16} />;
  if (stage === "CALL_AI") return <Zap size={16} />;
  return <Clock3 size={16} />;
}

/** 将数据库任务快照转换成页面刷新后可展示的一条时间线事件。 */
function restoredTaskEvent(task) {
  const messages = {
    PENDING: "诊断任务等待执行",
    RUNNING: "诊断任务正在执行",
    SUCCESS: "诊断任务执行成功",
    FAILED: "诊断任务执行失败",
  };
  const timestamp = task.finishedAt || task.startedAt || task.updatedAt || task.createdAt;
  return {
    ...task,
    stage: task.status,
    message: messages[task.status] || "诊断任务状态已恢复",
    timestamp,
    key: `RESTORED-${task.status}-${timestamp}`,
  };
}

function App() {
  // incidents 是左侧业务对象；task、events、audits 和 records 属于当前选中故障。
  const [scenarios, setScenarios] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [task, setTask] = useState(null);
  const [events, setEvents] = useState([]);
  const [audits, setAudits] = useState([]);
  const [aiAudits, setAiAudits] = useState([]);
  const [records, setRecords] = useState([]);
  const [incidentReport, setIncidentReport] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sourceRef = useRef(null);

  const selectedIncident = incidents.find((item) => item.id === selectedId) || null;
  const latestRecord = records[0] || null;

  function scrollToSection(sectionId) {
    document.getElementById(sectionId)?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  }

  const sortedIncidents = useMemo(
    () => [...incidents].sort(compareIncidents),
    [incidents],
  );

  /** 同时加载故障注入按钮和故障列表，并尽量保留当前选择。 */
  const loadBaseData = useCallback(async () => {
    try {
      const [scenarioData, incidentData] = await Promise.all([
        api.listScenarios(),
        api.listIncidents(),
      ]);
      setScenarios(scenarioData);
      setIncidents(incidentData);
      setSelectedId((current) => {
        const selectionStillExists = incidentData.some((item) => item.id === current);
        return selectionStillExists
          ? current
          : [...incidentData].sort(compareIncidents)[0]?.id || null;
      });
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    }
  }, []);

  useEffect(() => {
    loadBaseData();
    return () => sourceRef.current?.close();
  }, [loadBaseData]);

  /** 终态后一次性刷新数据库任务、工具/AI 审计和诊断报告。 */
  const loadTaskArtifacts = useCallback(async (taskId, incidentId) => {
    const [taskData, auditData, aiAuditData, recordData] = await Promise.all([
      api.getDiagnosisTask(taskId),
      api.listAudits(taskId),
      api.listAiAudits(taskId),
      api.listDiagnosisRecords(incidentId),
    ]);
    setTask(taskData);
    setAudits(auditData);
    setAiAudits(aiAuditData);
    setRecords(recordData);
  }, []);

  const subscribe = useCallback(
    (taskId, incidentId) => {
      // 切换任务前关闭旧连接，避免两个任务的事件混入同一条时间线。
      sourceRef.current?.close();
      const source = new EventSource(api.eventUrl(taskId));
      sourceRef.current = source;

      const handleEvent = (event) => {
        const payload = JSON.parse(event.data);
        setEvents((current) => {
          const key = `${payload.stage}-${payload.timestamp}`;
          return current.some((item) => item.key === key)
            ? current
            : [...current, { ...payload, key }];
        });
        setTask((current) => ({ ...current, ...payload, id: taskId }));

        if (payload.status === "SUCCESS" || payload.status === "FAILED") {
          source.close();
          loadTaskArtifacts(taskId, incidentId).catch((requestError) => {
            setError(requestError.message);
          });
        } else if (payload.stage?.startsWith("TOOL_")) {
          api.listAudits(taskId).then(setAudits).catch(() => {});
        }
      };

      EVENT_NAMES.forEach((name) => source.addEventListener(name, handleEvent));
      // 网络断开时用普通查询补一次最终状态，SSE 不是唯一可靠来源。
      source.onerror = () => {
        source.close();
        loadTaskArtifacts(taskId, incidentId).catch(() => {});
      };
    },
    [loadTaskArtifacts],
  );

  useEffect(() => {
    if (!selectedId) return undefined;

    let cancelled = false;
    sourceRef.current?.close();
    setTask(null);
    setEvents([]);
    setAudits([]);
    setAiAudits([]);
    setRecords([]);
    setIncidentReport(null);

    /** 页面刷新或切换故障时，从后端恢复最近任务及其持久化结果。 */
    async function restoreIncidentContext() {
      try {
        const [taskData, recordData] = await Promise.all([
          api.getLatestDiagnosisTask(selectedId),
          api.listDiagnosisRecords(selectedId),
        ]);
        if (cancelled) return;
        setRecords(recordData);

        if (!taskData) return;
        const [auditData, aiAuditData] = await Promise.all([
          api.listAudits(taskData.id),
          api.listAiAudits(taskData.id),
        ]);
        if (cancelled) return;

        setTask(taskData);
        setEvents([restoredTaskEvent(taskData)]);
        setAudits(auditData);
        setAiAudits(aiAuditData);
        if (taskData.status === "PENDING" || taskData.status === "RUNNING") {
          subscribe(taskData.id, selectedId);
        }
      } catch (requestError) {
        if (!cancelled) setError(requestError.message);
      }
    }

    restoreIncidentContext();
    return () => {
      cancelled = true;
      sourceRef.current?.close();
    };
  }, [selectedId, subscribe]);

  /** 创建演示故障，并把工作区切换到刚创建的 Incident。 */
  async function injectScenario(key) {
    setBusy(true);
    try {
      const result = await api.injectScenario(key);
      await loadBaseData();
      setSelectedId(result.incident.id);
      setTask(null);
      setEvents([]);
      setAudits([]);
      setAiAudits([]);
      setRecords([]);
      setIncidentReport(null);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  /** 创建异步任务后立即订阅 SSE，不等待 Python 诊断完成。 */
  async function startDiagnosis() {
    if (!selectedIncident) return;
    setBusy(true);
    try {
      const createdTask = await api.createDiagnosisTask(selectedIncident.id);
      setTask(createdTask);
      setEvents([]);
      setAudits([]);
      setAiAudits([]);
      setIncidentReport(null);
      subscribe(createdTask.id, selectedIncident.id);
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  /** 事故复盘通过 Tool Gateway 生成，因此同样会留下可查询的工具审计记录。 */
  async function generateIncidentReport() {
    if (!task || !selectedIncident) return;
    setBusy(true);
    try {
      const result = await api.generateIncidentReport(task.id, selectedIncident.id);
      if (result.status !== "SUCCESS") {
        throw new Error(result.errorMessage || "事故复盘生成失败");
      }
      setIncidentReport(result.data);
      setAudits(await api.listAudits(task.id));
      setError("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark"><Activity size={20} /></div>
          <div><strong>OpsMind AI</strong><span>SRE Console</span></div>
        </div>
        <nav>
          <button className="nav-item active" onClick={() => scrollToSection("diagnosis-console")}><CircleGauge size={18} />诊断控制台</button>
          <button className="nav-item" onClick={() => scrollToSection("incident-list")}><FileSearch size={18} />故障事件</button>
          <button className="nav-item" onClick={() => scrollToSection("call-audits")}><TerminalSquare size={18} />调用审计</button>
          <button className="nav-item" onClick={() => scrollToSection("diagnosis-report")}><BookOpenText size={18} />诊断报告</button>
        </nav>
        <div className="system-state">
          <span className="live-dot" />
          <div><strong>本地环境</strong><span>API / AI / Redis</span></div>
        </div>
      </aside>

      <main className="workspace" id="diagnosis-console">
        <header className="topbar">
          <div>
            <h1>诊断控制台</h1>
            <p>故障、Agent 工具调用与证据报告</p>
          </div>
          <button className="icon-button" title="刷新数据" onClick={loadBaseData}>
            <RefreshCw size={18} />
          </button>
        </header>

        {error && (
          <div className="error-banner">
            <AlertTriangle size={17} />
            <span>{error}</span>
            <button title="关闭" onClick={() => setError("")}>×</button>
          </div>
        )}

        <section className="scenario-strip">
          <div className="section-heading">
            <div><h2>故障注入</h2><p>创建可重复验证的演示事件</p></div>
          </div>
          <div className="scenario-actions">
            {scenarios.map((scenario) => (
              <button
                key={scenario.key}
                className="scenario-button"
                disabled={busy}
                onClick={() => injectScenario(scenario.key)}
              >
                <Zap size={16} />
                <span>{scenario.title}</span>
              </button>
            ))}
          </div>
        </section>

        <div className="content-grid">
          <section className="incident-panel" id="incident-list">
            <div className="section-heading compact">
              <div><h2>故障事件</h2><p>{incidents.length} 条记录</p></div>
            </div>
            <div className="incident-list">
              {sortedIncidents.map((incident) => (
                <button
                  className={`incident-row ${selectedId === incident.id ? "selected" : ""}`}
                  key={incident.id}
                  onClick={() => {
                    setSelectedId(incident.id);
                    setTask(null);
                    setEvents([]);
                    setAudits([]);
                    setAiAudits([]);
                    setIncidentReport(null);
                  }}
                >
                  <span className={`severity-dot severity-${incident.severity.toLowerCase()}`} />
                  <span className="incident-main">
                    <strong>{incident.title}</strong>
                    <small>{incident.serviceName} · {formatTime(incident.createdAt)}</small>
                  </span>
                  <StatusBadge status={incident.status} />
                  <ChevronRight size={16} />
                </button>
              ))}
              {!incidents.length && <div className="empty-state">暂无故障事件</div>}
            </div>
          </section>

          <section className="detail-panel">
            {selectedIncident ? (
              <>
                <div className="detail-header">
                  <div>
                    <div className="detail-meta">
                      <StatusBadge status={selectedIncident.severity} />
                      <span>{selectedIncident.serviceName}</span>
                      {task?.traceId && (
                        <code title={task.traceId}>Trace {task.traceId.slice(0, 12)}</code>
                      )}
                    </div>
                    <h2>{selectedIncident.title}</h2>
                    <p>{selectedIncident.symptom}</p>
                  </div>
                  <button className="primary-button" disabled={busy} onClick={startDiagnosis}>
                    <Play size={17} fill="currentColor" />
                    开始 AI 诊断
                  </button>
                </div>

                <div className="summary-grid">
                  <div className="summary-item"><ServerCog size={18} /><span>服务</span><strong>{selectedIncident.serviceName}</strong></div>
                  <div className="summary-item"><Gauge size={18} /><span>任务状态</span><strong>{task?.status || "未开始"}</strong></div>
                  <div className="summary-item"><Database size={18} /><span>工具调用</span><strong>{audits.length}</strong></div>
                  <div className="summary-item"><ShieldCheck size={18} /><span>报告</span><strong>{latestRecord ? "已生成" : "等待中"}</strong></div>
                </div>

                <div className="detail-columns">
                  <div className="timeline-block">
                    <div className="subheading"><h3>诊断时间线</h3><StatusBadge status={task?.status || "IDLE"} /></div>
                    <div className="timeline">
                      {events.map((event) => (
                        <div className={`timeline-item stage-${event.stage.toLowerCase()}`} key={event.key}>
                          <div className="timeline-icon"><StageIcon stage={event.stage} /></div>
                          <div><strong>{event.message}</strong><span>{event.stage} · {formatTime(event.timestamp)}</span></div>
                        </div>
                      ))}
                      {!events.length && <div className="empty-state compact-empty">启动诊断后显示实时过程</div>}
                    </div>
                  </div>

                  <div className="audit-block" id="call-audits">
                    <div className="subheading"><h3>调用审计</h3><span>{audits.length + aiAudits.length} 次</span></div>
                    <div className="audit-list">
                      {audits.map((audit) => (
                        <div className="audit-row" key={audit.id}>
                          <div className="tool-icon"><TerminalSquare size={16} /></div>
                          <div><strong>{audit.toolName}</strong><span>{audit.responseSummary || audit.errorMessage || "无摘要"}</span></div>
                          <div className="audit-meta"><StatusBadge status={audit.status} /><small>{audit.latencyMs}ms</small></div>
                        </div>
                      ))}
                      {aiAudits.map((audit) => (
                        <div className="audit-row" key={audit.id}>
                          <div className="tool-icon ai-icon"><Zap size={16} /></div>
                          <div>
                            <strong>{audit.executionMode || "DETERMINISTIC"} Agent</strong>
                            <span title={audit.promptVersion}>{audit.modelName}</span>
                          </div>
                          <div className="audit-meta">
                            <StatusBadge status={audit.status} />
                            <small>{audit.inputTokens + audit.outputTokens} tokens · {audit.latencyMs}ms</small>
                          </div>
                        </div>
                      ))}
                      {!audits.length && !aiAudits.length && <div className="empty-state compact-empty">暂无调用记录</div>}
                    </div>
                  </div>
                </div>

                <div className="report-block" id="diagnosis-report">
                  <div className="subheading">
                    <h3>诊断报告</h3>
                    <div className="report-actions">
                      {latestRecord?.agentMetadata && (
                        <StatusBadge status={latestRecord.agentMetadata.executionMode} />
                      )}
                      {latestRecord && <span>置信度 {Math.round(latestRecord.confidence * 100)}%</span>}
                      {latestRecord && task?.status === "SUCCESS" && (
                        <button
                          className="secondary-button"
                          disabled={busy}
                          onClick={generateIncidentReport}
                        >
                          <ListChecks size={15} />
                          生成事故复盘
                        </button>
                      )}
                    </div>
                  </div>
                  {latestRecord ? (
                    <div className="report-content">
                      <div className="report-lead">
                        <Activity size={20} />
                        <div>
                          <span>诊断摘要</span>
                          <strong>{latestRecord.summary}</strong>
                          {latestRecord.agentMetadata && (
                            <small className="report-runtime">
                              {latestRecord.agentMetadata.modelName} · {latestRecord.agentMetadata.promptVersion} · {formatAgentUsage(latestRecord.agentMetadata)}
                            </small>
                          )}
                        </div>
                      </div>
                      <div className="report-section"><h4>根因判断</h4><p>{latestRecord.rootCause}</p></div>
                      <div className="report-section"><h4>证据</h4><ul>{latestRecord.evidence.map((item) => <li key={item}>{item}</li>)}</ul></div>
                      <div className="report-section recommendation"><h4>处置建议</h4><p>{latestRecord.recommendation}</p></div>
                    </div>
                  ) : (
                    <div className="empty-state report-empty">完成诊断后将在这里展示根因、证据和建议</div>
                  )}
                </div>

                {incidentReport && (
                  <div className="postmortem-block">
                    <div className="subheading">
                      <h3>{incidentReport.title}</h3>
                      <span>{formatTime(incidentReport.generatedAt)}</span>
                    </div>
                    <div className="postmortem-grid">
                      <div className="postmortem-section">
                        <h4>影响范围</h4>
                        <p>{incidentReport.impact}</p>
                      </div>
                      <div className="postmortem-section">
                        <h4>确认根因</h4>
                        <p>{incidentReport.rootCause}</p>
                      </div>
                      <div className="postmortem-section">
                        <h4><Clock3 size={14} />关键时间线</h4>
                        <ul>{incidentReport.timeline.map((item) => <li key={item}>{item}</li>)}</ul>
                      </div>
                      <div className="postmortem-section">
                        <h4><GitCommitHorizontal size={14} />修复与预防</h4>
                        <ul>{incidentReport.correctiveActions.map((item) => <li key={item}>{item}</li>)}</ul>
                      </div>
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="empty-state detail-empty">选择一条故障事件查看诊断工作区</div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}

export default App;
