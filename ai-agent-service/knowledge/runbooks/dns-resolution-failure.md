# DNS 解析失败处理 Runbook

## 适用场景

当服务通过域名访问内部或外部依赖时无法获得地址、解析耗时升高或偶发解析超时，适用本 Runbook。常见日志关键词包括 `UnknownHostException`、`Name or service not known`、`Temporary failure in name resolution`、`SERVFAIL`、`NXDOMAIN`、`DNS lookup timeout`、`i/o timeout`、`域名解析失败`；常见指标包括 DNS request rate、DNS latency、`coredns_dns_responses_total{rcode}`、`coredns_dns_request_duration_seconds`、resolver error count 和接口错误率；Trace 中可能出现 DNS lookup span 超时、`error.type=UnknownHostException`，或在目标 HTTP/TCP span 建立连接前出现较长空白。

应用日志中的单次 `UnknownHostException` 可能来自错误域名。更明确的基础设施证据是多个实例或服务对不同合法域名同时解析失败，且 DNS 服务端的 SERVFAIL、超时或延迟在同一时间窗口内升高。

## 常见原因

1. DNS 记录缺失、名称拼写错误、记录变更未生效或负缓存仍在有效期内。
2. CoreDNS、企业 DNS 或上游权威服务器异常、过载或配置错误。
3. 容器的 `/etc/resolv.conf`、search domain 或 `ndots` 配置导致查询路径异常。
4. NetworkPolicy、防火墙或安全组阻断 UDP/TCP 53 端口。
5. 本地、JVM 或代理 DNS 缓存保留了过期地址。
6. A、AAAA、CNAME 或服务发现记录配置不一致，形成错误链路或循环。

## 排查步骤

1. 确认失败域名、返回码、解析耗时和受影响范围，区分单域名、单实例、单节点、单集群和全局故障。
2. 从发生故障的同一容器或网络命名空间执行 `getent hosts <domain>`、`nslookup <domain>` 或 `dig <domain>`，记录查询服务器、rcode、ANSWER、TTL 和耗时。
3. 检查 `/etc/resolv.conf` 中 nameserver、search 和 options，分别查询完整域名与短域名，排除 search domain 或 `ndots` 造成的额外查询。
4. 查询 A、AAAA 和 CNAME 链，并直接向指定 DNS 服务器及权威服务器发起查询，判断错误发生在应用侧缓存、集群 DNS、上游递归还是权威记录。
5. 检查 CoreDNS 或 DNS 服务的错误日志、SERVFAIL/NXDOMAIN 比例、请求延迟、Pod 健康和资源使用情况；同时验证 UDP/TCP 53 的网络策略和连通性。
6. 对比最近的 DNS 记录、服务名、Namespace、NetworkPolicy、CoreDNS 配置和应用发布变更，确认是否存在时间关联。

## 处理建议

1. 域名或记录配置错误时，按变更流程恢复正确的 A、AAAA、CNAME 或服务发现记录，并根据 TTL 预估传播时间。
2. DNS 服务容量不足时分批扩容或恢复异常实例，检查缓存命中和上游转发配置，避免请求继续集中到故障节点。
3. 网络策略阻断时仅放通到批准 DNS 服务的 UDP/TCP 53 流量，并验证命名空间和目标选择器范围。
4. 对可容忍短时解析失败的客户端配置有限次数、带退避的重试；避免无上限重试放大 DNS 压力。
5. 修复后从原故障容器重复解析并调用真实依赖，确认 rcode、地址、TTL、解析延迟和业务错误率恢复。

## 风险提示

不要通过全量修改 `/etc/hosts`、关闭 DNS 缓存或无差别刷新所有节点来长期绕过问题。临时固定 IP 会绕过服务发现、负载均衡和故障转移，地址变化后可能再次中断。修改 DNS 记录还可能将流量切换到错误环境；操作前必须核对域名、记录值、TTL、回滚方案和 TLS 证书覆盖范围。
