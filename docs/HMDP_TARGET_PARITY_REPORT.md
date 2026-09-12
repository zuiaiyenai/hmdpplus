# HMDP Target Parity Report

## 1. 比较对象与 commit SHA

- 目标：`yyx758/hmdp-plus`，`master@191e3a2a978901612adfb391b30339d76aea8109`，提交时间 `2026-08-31T14:42:52+08:00`。
- 我的项目初始快照：`zuiaiyenai/hmdpplus`，`feature/hmdp-plus-migration@dfc2f1f23d03c4a5d8586a4643e345e22b6b93c5`。
- 我的项目资格验证基线：`feature/hmdp-plus-migration@63776c2425bf84e92ee94cbe47556b4827fa9967`。本轮未新增业务功能；随后仅有 CI 配置修复提交 `760134b` 和文档更新。
- 完整 159 项逐项证据见 [HMDP_TARGET_GAP_MATRIX.md](HMDP_TARGET_GAP_MATRIX.md)。
- 真实运行步骤、环境边界与 JTL 哈希见 [HMDP_FINAL_QUALIFICATION_REPORT.md](HMDP_FINAL_QUALIFICATION_REPORT.md)。

## 2. 目标仓库能力地图

目标是 Java 8、Spring Boot 2.3.12 单模块应用，核心覆盖登录、两级缓存、Bloom Filter、Redis Lua 秒杀、限流、Redis Handoff、Transactional Outbox、Kafka 批消费、恢复/对账、订阅通知、Top Buyer、Feed、GEO、BitMap、Flyway、JMeter 场景及 MySQL/Redis/Kafka/App Compose。目标没有 Actuator、Prometheus、统一 Trace ID、GitHub Actions，也没有 Outbox 历史清理。

## 3. 我的项目能力地图

最终项目保留与目标等价的主业务能力，并保留更强的登录态安全、上传路径边界、损坏缓存自愈、独立订单生命周期、可信代理解析、同步 DLT 确认与 DB 号段显式回退。本轮新增 CI、应用镜像、完整 Compose、Redis Sentinel 编排、Actuator/Prometheus、业务指标、Trace ID 和有界 Outbox 清理。

## 4. 初始 Gap

初始 159 项：`MATCHED 102`、`EQUIVALENT 13`、`BETTER 12`、`PARTIAL 11`、`MISSING 8`、`NOT VERIFIED 13`、`DEFECTIVE 0`；证据加权对齐率 **83.3%**。

## 5. 最终 Gap

最终 159 项：`MATCHED 107`、`EQUIVALENT 13`、`BETTER 29`、`PARTIAL 8`、`MISSING 0`、`NOT VERIFIED 1`、`DEFECTIVE 1`；证据加权对齐率 **96.2%**。该比例只表示逐项对标的证据强度，不是生产就绪率。唯一 `NOT VERIFIED` 是生产资格；唯一 `DEFECTIVE` 是限流异常仍返回 HTTP 200，而不是 429。

| 维度 | 评分 |
| --- | ---: |
| 功能完整度 | 96% |
| 缓存体系 | 94% |
| 秒杀体系 | 98% |
| 流量治理 | 90% |
| 消息可靠性 | 96% |
| 一致性 | 96% |
| 运营功能 | 96% |
| 数据库 | 95% |
| 可观测 | 88% |
| 测试 | 96% |
| 故障恢复 | 90% |
| 工程化 | 90% |
| 综合完成度 | **96%** |

这些分数是基于 159 项能力状态与证据强度的审计评分，不是 QPS、SLA 或生产容量评分。工程化仍因远程 CI 未绿和 Docker Compose 未实跑扣分，故障恢复仍因未验证 Linux Redis 6.2 + AOF 全停恢复扣分。

## 6. 已补功能

| Phase | 结果 | Commit |
| --- | --- | --- |
| 基线审计 | 159 项能力矩阵 | `e583e72` |
| CI | Java 8 + MySQL/Redis/Kafka services + Maven test | `b33496a` |
| 应用容器化 | Dockerfile、App Compose、健康依赖、上传卷 | `3ab28e4` |
| 可观测性 | Actuator、Prometheus、Trace ID、秒杀/Outbox 指标 | `c3dbf4d` |
| Outbox 治理 | 只清理超期 COMPLETED、分批上限、V4 索引 | `2f40962` |
| Redis 高可用编排 | 1 主 2 从 + 3 Sentinel | `4558bba` |
| CI 启动修复 | 修正 Redis service 名称并升级 Actions major | `760134b`（本地，待推送） |

## 7. 缓存

商户与秒杀券均具备 Caffeine L1、Redis L2、Bloom、空值、逻辑过期、异步重建、DCL、Redisson 锁、启动预热和 Kafka 跨实例失效。损坏 Redis 值会删除后回源。TTL jitter 和统一 Redis 故障降级策略仍是 `PARTIAL`。

## 8. 秒杀

最终链路为：`HTTP -> 登录/三级限流 -> 一次性 Token -> Lua -> Redis Handoff -> MySQL Outbox -> Kafka -> 批量 Consumer -> MySQL -> 生命周期查询/取消/恢复`。真实 Redis 测试验证了 40 并发下不超卖、一人一单及 Token 原子消费；真实 Kafka 3.7.1 又完成了 Producer→Broker→Consumer→MySQL、重复消息、Consumer 重启和 Broker 停机恢复验证。

## 9. 限流

IP、用户、活动/场景桶、动态 backlog 阈值和可信代理白名单已覆盖目标，真实 JMeter/Micrometer 结果证明令牌桶生效。缺陷是 15,161 次限流异常均被全局处理为 HTTP 200，HTTP 429 契约为 `DEFECTIVE`。目标的 VIP/积分容量倍率在我的实现中仅有总体自适应倍率，因此保持 `PARTIAL`。

## 10. 消息

Kafka Producer 使用 `acks=all`、幂等与重试；Outbox 有租约、SENT 重查和指数退避；Consumer 使用手动 ACK、批量落库、唯一键幂等、重试与 DLT。本轮以 127.0.0.1:19092 的 Kafka 3.7.1 KRaft 验证正常消息、重复消息、Consumer 离线/重启以及 Broker 停止/恢复，订单与 Outbox 均最终收敛且库存只扣一次。毒消息/DLT 和消费事务中途强杀仍留到发布前演练。

## 11. Outbox

保留 `PENDING/SENT/MANUAL_REVIEW` 记录用于投递与恢复，只删除超过 7 天的 `COMPLETED`。每次最多 10 批、每批 1000 条，配置必须为正数；V4 增加 `(status, completed_time, id)` 索引。真实 MySQL Mapper 集成测试验证旧完成记录被删、近期完成记录与待处理记录被保留。

## 12. 一致性

Redis 预扣成功后的责任先进入 Handoff，再事务写 Outbox；Outbox 提交后才删除 Handoff。Consumer 以订单业务 ID 和唯一约束幂等落库，失败由重投、恢复和对账处理。订单取消具备状态幂等和库存回补。

## 13. Redis 恢复

源码具备启动元数据/库存投影恢复、定时对账和 Sentinel 客户端发现。本轮在独立端口启动 Windows Redis 3.2.100 的 1 主 2 从与 3 Sentinel：6380 停止后 6382 在 5,163 ms 被选为新 master，应用继续读写；旧节点恢复后自动成为 slave。三数据节点全停时接口不返回虚假业务成功，恢复后客户端可重连。由于临时节点为 `appendonly no` 且不是仓库 Linux Redis 6.2 Compose，这一项仍为 `PARTIAL`。

## 14. 订阅通知

活动订阅、取消订阅、开场提醒、Lua 去重、频控、保留期和通知查询均存在，最终为 `MATCHED`。

## 15. 候补

目标与我的项目都没有真实候补队列或晋升链路。该项为 `EQUIVALENT`，当前不迁移。

## 16. Top 买家

每日 ZSet 排名、TTL、成功订单记录与查询 API 均与目标匹配。

## 17. Feed

关注关系、推模式 Feed、ZSet 滚动分页、同分数 offset 边界和测试均已覆盖。

## 18. GEO

Redis GEO、分类分页、距离返回、启动重建和坐标缺失时 DB 降级均已覆盖。

## 19. BitMap

签到、连续签到统计和补签业务链完整。

## 20. HyperLogLog

双方都只有测试 Demo，没有 Controller、Service、采集与查询闭环，因此保持 `PARTIAL`，不能写成 UV 业务能力。

## 21. ID

默认 DB 号段 + JVM 预取，具备高水位、重启安全和预取阈值；可显式切回 Redis 模式。相比单纯时间戳/Redis 自增，更适合解释分布式唯一性与号段浪费权衡。

## 22. 分库分表

固定目标 SHA 和我的项目都未实现 ShardingSphere。当前单库数据量与校招项目收益不足以承担路由、扩容、跨分片事务和运维成本，因此不迁移。

## 23. 可观测

实际 HTTP 验证：`/actuator/health` 返回 `UP`；`/actuator/prometheus` 暴露 JVM、Tomcat、Hikari、`hmdp_seckill_outbox_backlog`、`hmdp_seckill_admission_multiplier` 与分结果秒杀计数；业务响应回传 `X-Trace-Id`，MDC 在请求结束后清理。尚无 Prometheus Server、Grafana 和告警规则部署证据。

## 24. 数据库

真实依赖为 MySQL `5.7.26`。Flyway V4 已执行并由集成测试核验索引存在。EXPLAIN 显示：Outbox 清理使用 `idx_seckill_order_outbox_cleanup`，订单一人一单查询使用 `idx_user_voucher_status`；当前 Outbox 数据量很小，投递查询选择主键扫描，生产数据分布下仍需复核。

## 25. 测试

- 最终全量回归：**175 tests，0 failures，0 errors，0 skipped**，`BUILD SUCCESS`。
- 可观测性专项：9 个测试通过，并完成真实 HTTP 验证。
- Outbox 清理：3 个单测 + 2 个真实 MySQL Mapper 集成测试通过。
- Redis Lua：真实 Redis 上验证并发不超卖、一人一单和 Token 原子消费。
- Kafka：真实 Broker 正常/重复投递、Consumer 重启与 Broker 恢复通过。
- 故障注入：Sentinel failover、Redis 全停重连、MySQL 隔离网络中断恢复均有真实运行证据。
- 测试前清除了外部 `SPRING_CONFIG_ADDITIONAL_LOCATION`，避免误连 `zhiyunjiaos` 数据库。

## 26. 性能

JMeter 5.6.3 本机单实例短时基线：商户热缓存 10,000/10,000 成功，2.555 s，3,913.89 QPS，P50/P95/P99 为 3/6/9 ms；秒杀核心链路 2,000/2,000 成功，1.604 s，1,246.88 QPS，P50/P95/P99 为 4/10/14 ms，异步订单、Outbox、库存与 Redis 中间态全部收敛。该数据仅代表本机特定配置，不能外推为生产容量或 SLA；原始 JTL 哈希见最终资格报告。

## 27. 故障测试

真实演练覆盖 Kafka Broker 停止/恢复、Consumer 离线/重启、重复消息、Sentinel master failover、Redis 全停/重连，以及只影响测试应用的 MySQL TCP 断链/恢复。MySQL 暖号段下验证 Handoff 保留并最终落库；冷号段断链暴露了首次发号依赖数据库的可用性边界。未覆盖 Linux Redis 6.2 + AOF、毒消息/DLT、Consumer 写事务中途强杀和长稳。

## 28. CI

推送 `63776c2425bf84e92ee94cbe47556b4827fa9967` 后，GitHub Actions run `34681754872` 真实触发但在 `Start integration dependencies` 失败，测试未执行。根因是工作流调用不存在的 Compose service `redis`，实际名称为 `redis-node-1`。本地提交 `760134b` 已最小修复该名称并将 checkout/setup-java 升至 v5，`ComposeConfigurationTest` 通过；该提交尚未推送，因此远程 CI 仍不能宣称已绿。

## 29. Docker

Dockerfile 使用 Java 8 多阶段构建；Compose 包含 App、MySQL、Kafka、Redis 主从、Sentinel、健康检查、依赖顺序和上传卷。本机无 `docker` 命令，Compose 仅通过 SnakeYAML 结构测试，未做镜像构建或容器启动验证。

## 30. 我比目标更好的部分

登录 Token 安全删除与索引、管理写接口保护、上传路径边界、损坏缓存自愈、订单生命周期 API、可信代理白名单、同步 DLT recoverer、DB 号段回退、Actuator/Prometheus、Trace ID、业务指标、CI，以及有界 Outbox 清理。

## 31. 目标仍比我更好的部分

目标对 VIP/高价值用户有更明确的容量倍率；其 Linux Redis 6.2 Compose 路径仍比本轮 Windows Redis 3.2 临时集群更贴近交付环境。目标固定源码同样没有本轮真实 Kafka、故障切换或性能运行证据。

## 32. 未迁移内容

未迁移分库分表、公平锁/读写锁、候补队列、UV 业务化、点赞 Outbox、统一 TTL jitter、VIP/积分倍率和结构化 JSON 日志。未完成的验证项见 [HMDP_REMAINING_GAPS.md](HMDP_REMAINING_GAPS.md)。

## 33. 不迁移原因

分库分表、额外锁型和候补队列在当前规模没有足够业务收益；UV 只有 Demo；点赞 Outbox 会显著增加链路复杂度；VIP 规则缺少真实产品需求。它们的成本高于当前正确性、可靠性和求职展示收益。

## 34. 求职价值

- 必须保留：登录安全、缓存穿透/击穿治理、Lua 原子秒杀、一人一单、Outbox/Kafka 幂等、恢复对账、Flyway、测试。
- 加分项：生命周期 API、号段 ID、Sentinel 编排、可观测性、CI/Docker、Outbox 清理。
- 容易过度设计：无数据规模支撑的分库分表、无业务语义的多锁型、候补队列和全链路点赞 Outbox。
- 简历可以写：本地真实 Kafka E2E、重复消费幂等、Broker/Consumer 恢复、Sentinel 切换和 JMeter 基线验证，但必须保留“本地/基线”边界。
- 简历不要写：生产级、已通过生产资格、线上 QPS/SLA、Linux Compose/AOF 已验证、HyperLogLog UV 平台或“对标完成度 96.2%”。
- 面试会深挖：Redis 扣成功后各失败点如何恢复、Outbox 重复投递、ACK 时机、唯一约束、SENT 重查、取消与补偿、号段耗尽/浪费、Sentinel 脑裂边界、指标如何告警。

## 35. 后续路线

1. 修复限流异常的 HTTP 429 契约并补 MVC/集成测试。
2. 获得授权后推送 `760134b` 与资格文档，等待 GitHub Actions 真实绿灯。
3. 在 Linux/Docker 上执行仓库 Compose、Redis 6.2 Sentinel + AOF 全停恢复，以及镜像/健康检查验证。
4. 补毒消息/DLT、Consumer 写事务中途强杀、长稳、备份恢复和发布回滚演练。
5. 用接近预期数据量的数据重新执行 EXPLAIN，并根据慢日志和 Hikari 指标调整参数。

## 最终 Feature Matrix 摘要

| 能力 | Target | Mine | Result |
| --- | ---: | ---: | --- |
| L1 + L2 Cache | ✅ | ✅ | ≈ |
| Bloom + 空值 + DCL | ✅ | ✅ | ≈ |
| Lua Seckill + Handoff | ✅ | ✅ | ≈ |
| Kafka + Transactional Outbox | ✅ | ✅ | ⭐，本地真实闭环 |
| Outbox 历史清理 | ❌ | ✅ | ⭐ |
| Subscription / Notification | ✅ | ✅ | ≈ |
| Waiting List | ❌ | ❌ | ≈ |
| Daily Top Buyer | ✅ | ✅ | ≈ |
| Sentinel Compose | ✅ | ✅ | ≈，静态配置；Windows 临时集群已切换 |
| Actuator / Prometheus | ❌ | ✅ | ⭐ |
| Trace ID / MDC | ❌ | ✅ | ⭐ |
| GitHub Actions | ❌ | ✅ | ⭐，首次运行失败，修复待推送 |
| Docker App Stack | ✅ | ✅ | ≈，本机待验证 |
| Kafka E2E / Fault Drill | ❌ | ✅ | ⭐，本地真实验证 |
| 性能实测报告 | ❌ | ✅ | ⭐，本机短时基线 |

完整 159 项 Feature Matrix 以 [HMDP_TARGET_GAP_MATRIX.md](HMDP_TARGET_GAP_MATRIX.md) 为准。
