# CPU 使用率饱和处理 Runbook

## 适用场景

当服务延迟和错误率升高，同时主机、容器或进程 CPU 长时间接近上限时，适用本 Runbook。常见日志关键词包括 `CPU throttling`、`request timeout`、`线程池队列已满`、`RejectedExecutionException`、`GC overhead limit exceeded`；常见指标包括 `process.cpu.usage`、`system.cpu.usage`、`container_cpu_usage_seconds_total`、`container_cpu_cfs_throttled_seconds_total`、load average、runnable threads 和接口 P95/P99 延迟；Trace 中可能出现多个业务 span 同时变慢、服务自身处理时间占比升高，而下游依赖耗时没有同步增加。

单次 CPU 峰值不能证明 CPU 饱和。更明确的证据是 CPU 持续超过告警阈值、容器 throttling 或运行队列持续增长，并且同一时间窗口内请求延迟、超时或服务自身 span 耗时同步恶化。

## 常见原因

1. 流量突增、批处理或定时任务集中执行，计算量超过当前实例容量。
2. 新版本引入死循环、忙等待、低效正则、频繁序列化、压缩或加密等 CPU 热点。
3. Java 垃圾回收过于频繁，应用线程与 GC 线程争抢 CPU。
4. 线程池配置不合理，活跃线程过多并发生上下文切换。
5. 容器 CPU limit 过低，或者节点存在资源争用，导致 CPU throttling。
6. 单个热点请求、异常重试或缓存失效放大了重复计算。

## 排查步骤

1. 对齐 CPU、请求量、错误率和 P95/P99 延迟的时间窗口，确认异常是否同时开始，并判断影响范围是单实例、单节点还是全部副本。
2. 检查 `container_cpu_cfs_throttled_seconds_total`、CPU limit、load average 和 runnable threads。CPU 使用率不高但 throttling 持续增加时，应优先检查容器配额。
3. 在受影响实例上使用 `top`、`pidstat -u -p <pid> 1` 或容器监控定位高 CPU 进程；Java 服务可使用 `top -H -p <pid>` 定位热点线程，并将线程 ID 与线程栈对应。
4. 获取短时间的线程栈、JFR 或低开销 CPU profile，确认热点是否集中在业务循环、GC、序列化、正则、加密或重试逻辑。连续采样应看到相同调用栈反复占用 CPU，才可作为代码热点证据。
5. 检查 Trace 中服务自身 span 与下游 span 的耗时分布。如果下游耗时正常、服务自身耗时显著升高，可进一步支持本地计算瓶颈判断。
6. 对比异常前后的发布、配置、流量和定时任务记录；若只有新版本实例异常，可先通过小范围回滚验证关联性。

## 处理建议

1. 流量突增时优先启用有边界的限流、请求排队或水平扩容，并持续观察错误率和队列长度。
2. 确认由新版本引起时，按发布流程回滚问题版本；保留必要的线程栈和 profile 作为后续修复证据。
3. 对已定位的热点逻辑减少重复计算，修复忙等待或无限重试，并为高成本操作增加缓存、批处理上限或超时。
4. GC 占用过高时先分析对象分配速率和堆使用情况，再调整堆或 GC 参数，避免仅通过扩大资源掩盖内存问题。
5. 确认 CPU limit 与正常负载不匹配后，分批调整资源配额，并验证节点余量和调度影响。

## 风险提示

不要仅凭瞬时 CPU 峰值重启全部实例或直接提高所有容器的 CPU limit。批量重启会降低可用容量，提高配额也可能挤压同节点其他服务。生产环境采集 JFR、线程栈或 profile 前应确认工具开销、采样时长和数据敏感性，并优先在单个实例上操作。
