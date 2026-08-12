# 消息队列积压处理 Runbook

## 适用场景

当消息生产速率持续高于消费速率、消费延迟明显增加或最老消息等待时间超过业务目标时，适用本 Runbook。常见日志关键词包括 `consumer lag`、`消息积压`、`rebalance in progress`、`CommitFailedException`、`max.poll.interval.ms exceeded`、`delivery acknowledgement timeout`、`nack`、`retry exhausted`、`dead-letter queue`；常见指标包括 `kafka_consumer_group_lag`、`records_lag_max`、`rabbitmq_queue_messages_ready`、`messages_unacknowledged`、生产/消费速率、重试次数和 oldest message age；Trace 中可关注 `messaging.destination`、partition、offset、producer send span 与 consumer process span 的间隔和处理耗时。

队列长度短时升高不一定构成故障。更明确的证据是 lag 或 oldest message age 持续增长、消费速率低于生产速率，并且消费者错误、处理耗时或下游依赖延迟在同一时间窗口内异常。

## 常见原因

1. 流量或批量任务导致生产速率突然增加，现有消费者容量不足。
2. 消费者实例异常、频繁重启或持续 rebalance，实际可用消费并发下降。
3. 数据库、第三方接口等下游依赖变慢，拉长单条消息处理时间。
4. 毒性消息反复重试，阻塞同一分区或占满重试队列。
5. 分区数、消费者数和 key 分布不匹配，出现热点分区或消费者空闲。
6. 消费批次、确认、预取或 `max.poll.interval.ms` 配置与处理耗时不匹配。

## 排查步骤

1. 确认受影响的 topic、queue、consumer group、partition 和时间范围，记录当前 lag、oldest message age、生产速率与消费速率。
2. 检查消费者实例健康状态、重启次数、rebalance 日志、消费线程池和连接状态，确认实际在线消费者数量。
3. 按 partition 查看 lag 和吞吐分布。少数 partition 持续增长时，检查消息 key 是否造成热点；全部 partition 增长时，检查整体容量或共同下游。
4. 检查 consumer process span 及其数据库、HTTP 子 span，定位消息处理本身慢还是下游调用慢；用 offset、message ID 或 correlation ID 关联日志与 Trace。
5. 查看重试次数、失败原因和死信队列，抽样检查是否存在同一消息反复 `nack`、反序列化失败或业务校验失败。
6. 对比异常前后的发布、消费者配置、分区调整和生产流量，确认是否由新版本或批任务触发。

## 处理建议

1. 消费逻辑健康且分区允许时，分批扩容消费者；Kafka 消费者数量不应盲目超过可并行分区数。
2. 下游过载时启用生产端限流、消费端背压或降低批处理并发，先保护数据库和关键依赖。
3. 将确认有问题的毒性消息隔离到死信队列，保留 message ID、失败原因和原始载荷引用，修复后按幂等策略重放。
4. 优化慢处理逻辑，合理调整批次、预取、poll 间隔和确认超时，并通过压测验证吞吐和单条处理时延。
5. 恢复后持续观察 lag 是否稳定下降，估算清空积压所需时间，并验证重复消费、乱序和业务补偿情况。

## 风险提示

不得为快速清空积压而直接删除队列、跳过 offset、清空死信队列或无记录地重放消息。这些操作可能造成数据丢失、重复扣款、状态乱序或下游雪崩。扩容消费者和提高批量大小前必须确认业务幂等性、分区顺序要求以及下游剩余容量。
