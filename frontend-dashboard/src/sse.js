/**
 * 建立由持久化任务状态兜底的 SSE 订阅。
 *
 * 浏览器原生 EventSource 会自动重试，但无法在重试前判断任务是否已经进入终态。
 * 这里显式关闭故障连接、查询数据库状态，再以指数退避重建连接。
 */
export function createResilientEventSubscription({
  url,
  eventNames,
  onEvent,
  readCurrent,
  onTerminal,
  eventSourceFactory = (eventUrl) => new EventSource(eventUrl),
  schedule = (callback, delay) => setTimeout(callback, delay),
  cancelSchedule = (timer) => clearTimeout(timer),
  baseDelayMs = 500,
  maxDelayMs = 5000,
}) {
  let source = null;
  let reconnectTimer = null;
  let reconnectAttempt = 0;
  let recovering = false;
  let closed = false;

  const isTerminal = (task) =>
    task?.status === "SUCCESS" || task?.status === "FAILED";

  function closeSource() {
    source?.close();
    source = null;
  }

  function close() {
    closed = true;
    recovering = false;
    closeSource();
    if (reconnectTimer !== null) {
      cancelSchedule(reconnectTimer);
      reconnectTimer = null;
    }
  }

  async function recover() {
    if (closed || recovering) return;
    recovering = true;
    closeSource();

    let currentTask = null;
    try {
      currentTask = await readCurrent();
    } catch {
      // 后端重启期间普通查询也可能失败，仍继续进入有界退避重连。
    }
    if (closed) return;
    if (isTerminal(currentTask)) {
      closed = true;
      recovering = false;
      onTerminal(currentTask);
      return;
    }

    const delay = Math.min(
      baseDelayMs * (2 ** reconnectAttempt),
      maxDelayMs,
    );
    reconnectAttempt += 1;
    reconnectTimer = schedule(() => {
      reconnectTimer = null;
      recovering = false;
      open();
    }, delay);
  }

  function open() {
    if (closed) return;
    const nextSource = eventSourceFactory(url);
    source = nextSource;
    const handleEvent = (event) => {
      reconnectAttempt = 0;
      const reachedTerminal = onEvent(event) === true;
      if (reachedTerminal) close();
    };
    eventNames.forEach((name) => nextSource.addEventListener(name, handleEvent));
    nextSource.onerror = recover;
  }

  open();
  return { close };
}
