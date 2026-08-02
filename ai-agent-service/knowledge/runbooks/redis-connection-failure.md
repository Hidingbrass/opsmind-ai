# Redis 连接失败处理 Runbook

## 适用场景

当 cache-service 或其他业务服务访问 Redis 时出现 Redis connection refused、Redis connection timeout、Redis command timeout、连接池等待超时、连接池耗尽等错误时，适用本 Runbook。

典型表现包括：缓存读取失败、缓存命中率下降、订单查询接口变慢、请求回源数据库增多，或者日志中出现 Redis 连接失败、connection refused、pool exhausted 等关键字。

## 常见原因

1. Redis 实例宕机、重启中，或者 Redis 端口不可达。
2. cache-service 到 Redis 的网络连接异常。
3. Redis 连接池配置过小，高峰期连接被耗尽。
4. Redis 慢命令、内存压力或 CPU 压力导致响应变慢。
5. 缓存降级策略缺失，Redis 异常后请求大量回源数据库。

## 排查步骤

1. 查看 cache-service 错误日志，确认是否出现 Redis connection refused、connection timeout、Redis command timeout 或连接池等待超时。
2. 检查 Redis 实例健康状态，确认 Redis 进程、端口、内存、CPU 和网络连通性是否正常。
3. 查看 Redis 连接池指标，例如活跃连接数、空闲连接数、等待连接数和连接获取超时次数。
4. 查看缓存命中率、缓存错误数和接口 P95/P99 延迟，确认是否出现缓存不可用导致的整体延迟升高。
5. 查看链路追踪中 Redis span 的状态、耗时和错误信息，确认异常是否集中在 Redis 调用节点。

## 修复建议

1. 如果 Redis 实例不可达，优先恢复 Redis 实例或切换备用 Redis。
2. 如果 Redis 连接池耗尽，临时扩大连接池上限，并排查是否存在连接泄漏或慢命令阻塞。
3. 对非核心缓存读取开启短时降级，避免 Redis 异常拖垮主流程。
4. 对热点 Key 增加本地缓存、请求合并或限流，降低 Redis 瞬时压力。
5. 恢复后补充 Redis 连接失败告警、连接池监控和缓存降级演练。

## 风险提示

不要在高峰期直接关闭全部缓存逻辑。缓存完全失效可能导致大量请求回源数据库，引发数据库慢查询、连接池耗尽或级联故障。应优先使用有边界的降级、限流和备用 Redis 策略。
