# HMDP 最终资格验证报告

## 1. 结论

验证对象为 `feature/hmdp-plus-migration@63776c2425bf84e92ee94cbe47556b4827fa9967`。本轮冻结业务功能，只执行真实依赖联调、故障注入、性能测试、CI 核验和文档更新。

| 门禁 | 结果 | 结论 |
| --- | --- | --- |
| Kafka Producer → Broker → Consumer → MySQL | 真实 Kafka 3.7.1 KRaft 闭环 | PASS |
| 重复消息与 Consumer 重启 | 不重复扣库存；离线消息在重启后消费 | PASS |
| Kafka 停机与 Outbox 恢复 | PENDING 重试，Broker 恢复后 COMPLETED | PASS |
| Sentinel master failover | 6380 → 6382，应用继续读写 | PASS（本机 Redis 3.2 范围） |
| MySQL 网络中断与恢复 | 暖号段下 Handoff 保留并最终落库 | PASS（隔离网络故障范围） |
| Redis 全停与恢复 | 失败不伪装成功，应用可重连 | PARTIAL |
| JMeter 缓存与秒杀基线 | 有真实 JTL、错误数和分位数 | PASS（本机基线） |
| GitHub Actions | 首次真实运行失败，修复已本地提交 | NOT PASSED |
| Docker Compose 整栈 | 当前机器无 Docker/WSL | NOT VERIFIED |
| 生产资格 | 尚缺 Linux Compose、AOF 持久性、长稳、备份恢复与发布回滚 | NO-GO |

因此可以描述为“已完成本地真实 Kafka E2E、故障恢复和 JMeter 基线验证”，仍不得描述为“生产级”或“已通过生产资格”。

## 2. 环境与证据边界

- Windows 11，Java 17.0.19 启动应用，Kafka 进程由 Java 25.0.3 启动。
- MySQL 5.7.26，主机端口 3306；故障演练通过只供测试应用使用的 13306 TCP 代理注入，不停止主机 MySQL。
- Kafka 3.7.1 KRaft，127.0.0.1:19092；发行包 SHA-512 与 Apache 官方值一致。
- JMeter 5.6.3；发行包 SHA-512 与 Apache 官方值一致。
- Sentinel 集群使用本机 Windows Redis 3.2.100，数据端口 6380–6382，Sentinel 26380–26382。
- Docker、Podman、WSL 均不可用；因此没有执行 `docker compose up`、镜像构建或 Linux Redis 6.2 演练。
- JMeter 数字是本机单实例短时基线，应用保持 DEBUG SQL 日志，不能外推为线上容量或 SLA。

## 3. Kafka E2E

### 3.1 正常投递

专用事件 `qual-kafka-e2e-20260912-01` 从 MySQL Outbox 的 `PENDING` 状态出发，由应用 Producer 发送到真实 Broker，再由批量 Consumer 写入 MySQL：

- Outbox：`PENDING → COMPLETED`；
- 订单 `9900001201001`：最终 1 行；
- 测试券库存：`5 → 4`。

### 3.2 重复投递

将同一 eventId/orderId 重新置为可投递状态后再次经过 Broker：

- 订单仍为 1 行；
- 库存仍为 4；
- Outbox 再次收敛为 `COMPLETED`。

### 3.3 Consumer 离线与重启

应用 Consumer 停止时，验证辅助 Producer 将消息写入 `hmdp.seckill-order.v1-1@0`：

- Consumer 离线时：Outbox `SENT`，订单 0 行；
- Consumer 重启后：Outbox `COMPLETED`，订单 1 行。

验证辅助程序只存在于被 Git 忽略的 `target/qualification-runtime`，没有进入业务源码。

## 4. Kafka 故障注入

停止本轮隔离 Broker 后插入事件 `qual-kafka-recovery-20260912-01`：

- Broker 停止时：Outbox 保持 `PENDING`，累计 5 次发送重试，`last_error=Kafka send failed`，订单 0 行；
- 同一 KRaft 数据目录恢复后：Outbox `COMPLETED`，订单落库，库存只扣减一次。

该结果验证了真实客户端超时、Outbox 保留和 Broker 恢复后的自动收敛。

## 5. Redis Sentinel 与 Redis 故障

### 5.1 Master failover

- 初始 master：127.0.0.1:6380；
- 停止 6380 后新 master：127.0.0.1:6382；
- Sentinel 观测选主耗时：5,163 ms；
- 切换后应用 `/user/me` 读取成功，`/user/sign` 写入成功；
- 旧 6380 恢复后自动成为 6382 的 slave，`master_link_status=up`；
- 登录态与签到位图在主从间一致，签到 key 的 BITCOUNT 均为 1。

### 5.2 全部数据节点停止

6380–6382 全停时，Redis 依赖接口返回 `success:false` 和 Trace ID，没有返回虚假成功；但 HTTP 状态仍为 200。三节点恢复后重新形成 1 主 2 从，重新写入专用登录态后应用读取成功，证明客户端可重连。

本轮临时 Redis 配置使用 `appendonly no`，全节点强制停止后最近登录态未持久化。因此这部分不能证明仓库 Compose 中 Redis 6.2 + AOF 的全停数据完整性，状态为 `PARTIAL`。

## 6. MySQL 故障注入

仅测试应用通过 127.0.0.1:13306 TCP 代理连接真实 MySQL，主机 3306 服务未停止。

- JVM 尚无数据库号段时断链：首次发号请求超时，Redis 库存和 Handoff 均未变化。这是当前 DB-segment 模式的冷启动边界。
- 正常请求预热号段后再次断链：HTTP 返回已受理订单号 `4611686018427407905`；MySQL 订单 0、Outbox 0；Redis 库存 1→0、已购用户 1、Handoff 1。
- 代理恢复后：订单落库，Outbox `COMPLETED`，Handoff 0，accepted 标记清除，MySQL/Redis 库存均为 0。

这证明了“已有号段”条件下 MySQL 故障时 Redis Handoff 不丢失及恢复后的最终一致性，也明确了冷启动无号段时的可用性限制。

## 7. JMeter 结果

### 7.1 商户缓存

测试接口 `/shop/2`，L1 开启。冷缓存先删除 Redis key；热缓存使用 40 线程、每线程 250 次，共 10,000 次。

| 场景 | 请求 | 成功 | 错误 | 持续时间 | QPS | P50 | P95 | P99 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 冷缓存单次 | 1 | 1 | 0 | 0.092 s | 不作为吞吐结论 | 92 ms | 92 ms | 92 ms | 92 ms |
| 热缓存 | 10,000 | 10,000 | 0 | 2.555 s | 3,913.89 | 3 ms | 6 ms | 9 ms | 19 ms |

### 7.2 秒杀核心 E2E

专用券库存 2,000，40 线程 × 50 个不同用户。为隔离核心链路容量，本场景关闭 Access Token 校验与入口限流，Kafka/Outbox 保持开启。

| 请求 | 成功 | 错误 | 持续时间 | QPS | P50 | P95 | P99 | Max |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2,000 | 2,000 | 0 | 1.604 s | 1,246.88 | 4 ms | 10 ms | 14 ms | 146 ms |

异步最终收敛：2,000 个订单、2,000 个不同用户、2,000 个 `COMPLETED` Outbox；MySQL/Redis 库存均为 0；Handoff 与 accepted 均为 0。

### 7.3 令牌桶

50 线程持续 5 秒请求同一用户的 Token 接口：

- 总请求 15,172，4.968 秒，约 3,053.95 req/s；
- P50/P95/P99：15/19/22 ms，Max 116 ms；
- Micrometer：11 次正常处理，15,161 次 `SeckillRateLimitException`；
- 所有响应均为 HTTP 200，因此 JMeter 显示 0 HTTP 错误。

限流本身生效，但 HTTP 429 契约未实现，是本轮发现的 `DEFECTIVE` 项。

### 7.4 原始 JTL 哈希

原始文件保存在本机 `target/qualification-runtime/evidence`，未纳入 Git：

| 文件 | SHA-256 |
| --- | --- |
| `shop-cache-cold.jtl` | `8A00603ADB9D6969261D8FEF4D9DB3576FB91D1F9465E5488C7F3B1014D357F6` |
| `shop-cache-hot.jtl` | `1AA89F7280EA7B187136A8A2DCE9D189B38979EF1DCAE46DB1B342EA01A40A91` |
| `seckill-e2e-2000-valid.jtl` | `DA5A18B2A74402F3403AFE7D80F4D0475D6390CA35FDAF4C6022E27793E80B5E` |
| `seckill-rate-limit-50x5s-valid.jtl` | `E0F28CF0EEE467FEF3DD4EBB7D8818FCFD936A2CFB0BFA93D4D3406EC92BC6C6` |

## 8. CI 真实结果

推送 `63776c2425bf84e92ee94cbe47556b4827fa9967` 后触发 GitHub Actions run `34681754872`，结论为 failure。失败发生在 `Start integration dependencies`，测试未执行。

根因是工作流调用不存在的 Compose service `redis`，实际 service 为 `redis-node-1`；同时 GitHub 对 `actions/checkout@v4` 与 `actions/setup-java@v4` 给出弃用警告。修复已作为本地独立提交 `760134b` 完成，并通过 `ComposeConfigurationTest`，但因外部推送审批未获授权，尚未产生修复后的远程 CI 结果。

## 9. 最终生产门禁

最终结论：**NO-GO（不可声明生产级）**。

上线前仍需完成：

1. 修复限流异常的 HTTP 429 契约，并补 MVC/集成测试；
2. 推送 `760134b`，取得 GitHub Actions 真实绿灯；
3. 在 Linux/Docker 上执行仓库 Compose 全栈、Redis 6.2 Sentinel + AOF 故障切换；
4. 执行毒消息/DLT、Consumer 处理中强杀、MySQL Consumer 写事务中断；
5. 执行更长时间的容量/长稳测试、资源监控、备份恢复与发布回滚演练。

当前可以进入求职展示和面试准备，但简历只能写“完成本地真实 Kafka E2E、Sentinel 切换及 JMeter 基线验证”，不能写“生产级高可用”或把本机 QPS 当作生产容量。
