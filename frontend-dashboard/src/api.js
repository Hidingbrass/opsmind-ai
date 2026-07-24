const API_BASE = import.meta.env.VITE_API_BASE_URL || "";

/**
 * 统一调用 Spring API，并从 Result<T> 外层结构中提取 data。
 * 非 2xx 或业务 code 非 0 时抛出异常，页面只需要处理一种失败方式。
 */
async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });

  const body = await response.json().catch(() => null);
  if (!response.ok || body?.code !== 0) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`);
  }
  return body.data;
}

/** 页面使用的后端接口集合，组件不直接拼接重复的 URL 和请求选项。 */
export const api = {
  listScenarios: () => request("/api/fault-scenarios"),
  injectScenario: (key) =>
    request(`/api/fault-scenarios/${key}/inject`, { method: "POST" }),
  listIncidents: () => request("/api/incidents"),
  createDiagnosisTask: (incidentId) =>
    request(`/api/diagnosis-tasks/incidents/${incidentId}`, { method: "POST" }),
  getDiagnosisTask: (taskId) => request(`/api/diagnosis-tasks/${taskId}`),
  getLatestDiagnosisTask: (incidentId) =>
    request(`/api/diagnosis-tasks?incidentId=${encodeURIComponent(incidentId)}`),
  listAudits: (taskId) =>
    request(`/api/tool-call-audits?taskId=${encodeURIComponent(taskId)}`),
  listAiAudits: (taskId) =>
    request(`/api/ai-call-audits?taskId=${encodeURIComponent(taskId)}`),
  listDiagnosisRecords: (incidentId) =>
    request(`/api/diagnoses/incidents/${incidentId}/records`),
  generateIncidentReport: (taskId, incidentId) =>
    request("/api/tools/execute", {
      method: "POST",
      body: JSON.stringify({
        taskId,
        incidentId,
        toolName: "generateIncidentReport",
        arguments: { incidentId },
      }),
    }),
  eventUrl: (taskId) =>
    `${API_BASE}/api/diagnosis-tasks/${encodeURIComponent(taskId)}/events`,
};
