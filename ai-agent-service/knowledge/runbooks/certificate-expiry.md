# TLS 证书过期与校验失败处理 Runbook

## 适用场景

当 HTTPS、mTLS、数据库 TLS 或消息队列 TLS 连接在握手阶段失败，且证据指向证书有效期、信任链或主机名校验时，适用本 Runbook。常见日志关键词包括 `certificate has expired or is not yet valid`、`x509: certificate has expired`、`SSLHandshakeException`、`CERTIFICATE_VERIFY_FAILED`、`unable to get local issuer certificate`、`hostname mismatch`、`证书已过期`；常见指标包括 `probe_ssl_earliest_cert_expiry`、certificate days remaining、TLS handshake error count、连接失败率和 5xx；Trace 中可能出现 TLS handshake span 失败、`error.type=SSLHandshakeException`、目标 `server.address` 与证书 SAN 不一致。

更明确的证据应同时包含失败端返回的校验错误，以及实际握手获得证书的 `notBefore`、`notAfter`、SAN、issuer 或 chain 异常。仅查看证书管理平台中的预期证书，不能证明线上终止层已经加载该证书。

## 常见原因

1. 叶子证书已经过期，或者自动续期任务失败。
2. 证书已经签发但负载均衡、Ingress、网关或应用仍加载旧 Secret/keystore。
3. 中间证书缺失、顺序错误或客户端信任库没有对应 CA。
4. 请求域名不在证书 SAN 中，或 SNI 指向了错误的虚拟主机。
5. 主机时间偏差导致证书被判断为 `not yet valid` 或已过期。
6. mTLS 客户端证书过期、用途不匹配或服务端信任关系未同步。

## 排查步骤

1. 确认失败的域名、端口、协议、客户端和 TLS 终止层，区分公网入口、内部服务、Ingress、Service Mesh 与应用自身证书。
2. 从与故障客户端相近的网络位置执行 `openssl s_client -connect <host>:<port> -servername <host> -showcerts`，记录实际返回证书的 subject、issuer、serial、fingerprint 和完整 chain。
3. 使用 `openssl x509 -noout -dates -subject -issuer -ext subjectAltName` 检查 `notBefore`、`notAfter` 和 SAN，并核对请求主机名。
4. 检查系统时间和 NTP 同步状态；若只有部分节点失败，比较节点时间、信任库、DNS 解析地址和代理路径。
5. 检查证书自动续期控制器、Secret/keystore 版本、挂载时间和 reload 日志，确认新证书是否已部署到真正的 TLS 终止实例。
6. 对照失败 Trace 与网关日志，确认握手失败发生在哪一跳；mTLS 场景还需分别检查客户端证书和服务端证书。

## 处理建议

1. 按证书管理流程重新签发包含正确 SAN、用途和完整中间链的证书，确认私钥与证书匹配。
2. 先在单个实例或预发布入口加载新证书，验证握手、主机名、信任链和业务请求后，再滚动更新其余实例。
3. 对不会自动 reload 的网关或应用执行受控 reload 或滚动重启，并确保更新期间仍有健康实例提供服务。
4. 修复自动续期、Secret 同步和到期告警，使告警窗口覆盖审批、签发和发布所需时间。
5. 完成后从内外部实际调用路径复查证书 fingerprint、到期时间、完整链和 TLS 握手错误率。

## 风险提示

不得通过关闭证书校验、接受所有主机名、扩大系统信任范围或使用明文协议来绕过故障，这会引入中间人攻击风险。私钥、keystore 和证书包不得写入日志、Runbook 或非授权工单。证书轮换涉及多个终止层时必须核对加载顺序和回滚证书，避免新旧信任关系不同步造成全链路中断。
