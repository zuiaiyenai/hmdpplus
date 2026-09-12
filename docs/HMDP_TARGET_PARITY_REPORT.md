# HMDP Target Parity Report

## 1. 比较对象与 commit SHA

- 目标：`yyx758/hmdp-plus`，`master@191e3a2a978901612adfb391b30339d76aea8109`，提交时间 `2026-08-31T14:42:52+08:00`。
- 我的项目初始快照：`zuiaiyenai/hmdpplus`，`feature/hmdp-plus-migration@dfc2f1f23d03c4a5d8586a4643e345e22b6b93c5`。
- 我的项目最终实现快照：`feature/hmdp-plus-migration@4558bba`。该 SHA 是最终文档提交前的代码快照；后续文档提交不改变业务实现。
- 完整 159 项逐项证据见 [HMDP_TARGET_GAP_MATRIX.md](HMDP_TARGET_GAP_MATRIX.md)。

## 2. 目标仓库能力地图

目标是 Java 8、Spring Boot 2.3.12 单模块应用，核心覆盖登录、两级缓存、Bloom Filter、Redis Lua 秒杀、限流、Redis Handoff、Transactional Outbox、Kafka 批消费、恢复/对账、订阅通知、Top Buyer、Feed、GEO、BitMap、Flyway、JMeter 场景及 MySQL/Redis/Kafka/App Compose。目标没有 Actuator、Prometheus、统一 Trace ID、GitHub Actions，也没有 Outbox 历史清理。

## 3. 我的项目能力地图

最终项目保留与目标等价的主业务能力，并保留更强的登录态安全、上传路径边界、损坏缓存自愈、独立订单生命周期、可信代理解析、同步 DLT 确认与 DB 号段显式回退。本轮新增 CI、应用镜像、完整 Compose、Redis Sentinel 编排、Actuator/Prometheus、业务指标、Trace ID 和有界 Outbox 清理。

## 4. 初始 Gap

初始 159 项：`MATCHED 102`、`EQUIVALENT 13`、`BETTER 12`、`PARTIAL 11`、`MISSING 8`、`NOT VERIFIED 13`、`DEFECTIVE 0`；证据加权对齐率 **83.3%**。

## 5. 最终 Gap

最终 159 项：`MATCHED 108`、`EQUIVALENT 13`、`BETTER 22`、`PARTIAL 7`、`MISSING 0`、`NOT VERIFIED 9`、`DEFECTIVE 0`；证据加权对齐率 **92.1%**。这里的 92.1% 不是生产就绪率，未运行的 Kafka、Sentinel 故障切换、故障注入、性能和生产验证没有计为完成。

| 维度 | 评分 |
| --- | ---: |
| 功能完整度 | 96% |
| 缓存体系 | 94% |
| 秒杀体系 | 97% |
| 流量治理 | 93% |
| 消息可靠性 | 86% |
| 一致性 | 94% |
| 运营功能 | 96% |
| 数据库 | 93% |
| 可观测 | 88% |
| 测试 | 90% |
| 故障恢复 | 78% |
| 工程化 | 92% |
| 综合完成度 | **92%** |

这些分数是基于 159 项能力状态与证据强度的审计评分，不是 QPS、SLA 或生产容量评分；消息、故障恢复和工程化因缺真实 Kafka/Docker 演练被主动扣分。

## 6. 已补功能

| Phase | 结果 | Commit |
| --- | --- | --- |
| 基线审计 | 159 项能力矩阵 | `e583e72` |
| CI | Java 8 + MySQL/Redis/Kafka services + Maven test | `b33496a` |
| 应用容器化 | Dockerfile、App Compose、健康依赖、上传卷 | `3ab28e4` |
| 可观测性 | Actuator、Prometheus、Trace ID、秒杀/Outbox 指标 | `c3dbf4d` |
| Outbox 治理 | 只清理超期 COMPLETED、分批上限、V4 索引 | `2f40962` |
| Redis 高可用编排 | 1 主 2 从 + 3 Sentinel | `4558bba` |

## 7. 缓存

商户与秒杀券均具备 Caffeine L1、Redis L2、Bloom、空值、逻辑过期、异步重建、DCL、Redisson 锁、启动预热和 Kafka 跨实例失效。损坏 Redis 值会删除后回源。TTL jitter 和统一 Redis 故障降级策略仍是 `PARTIAL`。

## 8. 秒杀

最终链路为：`HTTP -> 登录/三级限流 -> 一次性 Token -> Lua -> Redis Handoff -> MySQL Outbox -> Kafka -> 批量 Consumer -> MySQL -> 生命周期查询/取消/恢复`。真实 Redis 测试验证了 40 并发请求下不超卖、一人一单及 Token 原子消费；Kafka 后半链尚未做真实 Broker E2E。

## 9. 限流

IP、用户、活动/场景桶、动态 backlog 阈值、HTTP 429 和可信代理白名单已覆盖目标。目标的 VIP/积分容量倍率在我的实现中仅有总体自适应倍率，因此保持 `PARTIAL`，不为展示复杂度机械迁移。

## 10. 消息

Kafka Producer 使用 `acks=all`、幂等与重试；Outbox 有租约、SENT 重查和指数退避；Consumer 使用手动 ACK、批量落库、唯一键幂等、重试与 DLT。所有这些有源码和单测证据，但本机 `9092` 无监听，不能替代真实 Broker 验证。

## 11. Outbox

保留 `PENDING/SENT/MANUAL_REVIEW` 记录用于投递与恢复，只删除超过 7 天的 `COMPLETED`。每次最多 10 批、每批 1000 条，配置必须为正数；V4 增加 `(status, completed_time, id)` 索引。真实 MySQL Mapper 集成测试验证旧完成记录被删、近期完成记录与待处理记录被保留。

## 12. 一致性

Redis 预扣成功后的责任先进入 Handoff，再事务写 Outbox；Outbox 提交后才删除 Handoff。Consumer 以订单业务 ID 和唯一约束幂等落库，失败由重投、恢复和对账处理。订单取消具备状态幂等和库存回补。

## 13. Redis 恢复

源码具备启动元数据/库存投影恢复、定时对账和 Sentinel 客户端发现。本轮补齐 1 主 2 从与 3 Sentinel 编排，但因没有 Docker，主从切换、客户端重连和恢复时间未实测。

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

- 全量：**175 tests，0 failures，0 errors，0 skipped**。
- 可观测性专项：9 个测试通过，并完成真实 HTTP 验证。
- Outbox 清理：3 个单测 + 2 个真实 MySQL Mapper 集成测试通过。
- Redis Lua：真实 Redis 上验证并发不超卖、一人一单和 Token 原子消费。
- 测试前清除了外部 `SPRING_CONFIG_ADDITIONAL_LOCATION`，避免误连 `zhiyunjiaos` 数据库。

## 26. 性能

仓库存在 JMeter 场景，但本机没有 JMeter、Docker 和 Kafka，因此本轮没有合法的 QPS/P50/P95/P99 数据。禁止把单测耗时或一次 curl 响应包装成性能结论。

## 27. 故障测试

单测覆盖 Redis 异常、Kafka send future 失败、数据库异常保留 Handoff、重复消息和 DLT 分支。真实 Redis/MySQL/Kafka 停机、Broker/Consumer 重启和 Sentinel failover 未执行，仍为 `NOT VERIFIED`。

## 28. CI

`.github/workflows/ci.yml` 在 push/PR 时启动 MySQL、Redis、Kafka，并以 Java 8 执行 Maven 测试。工作流尚未推送触发，所以只能确认配置与本地测试，不能宣称远程 CI 已绿。

## 29. Docker

Dockerfile 使用 Java 8 多阶段构建；Compose 包含 App、MySQL、Kafka、Redis 主从、Sentinel、健康检查、依赖顺序和上传卷。本机无 `docker` 命令，Compose 仅通过 SnakeYAML 结构测试，未做镜像构建或容器启动验证。

## 30. 我比目标更好的部分

登录 Token 安全删除与索引、管理写接口保护、上传路径边界、损坏缓存自愈、订单生命周期 API、可信代理白名单、同步 DLT recoverer、DB 号段回退、Actuator/Prometheus、Trace ID、业务指标、CI，以及有界 Outbox 清理。

## 31. 目标仍比我更好的部分

目标对 VIP/高价值用户有更明确的容量倍率；其已有 JMeter 使用说明和 Sentinel 编排历史更完整。目标固定源码同样没有本轮真实 Kafka、故障切换或性能运行证据。

## 32. 未迁移内容

未迁移分库分表、公平锁/读写锁、候补队列、UV 业务化、点赞 Outbox、统一 TTL jitter、VIP/积分倍率和结构化 JSON 日志。未完成的验证项见 [HMDP_REMAINING_GAPS.md](HMDP_REMAINING_GAPS.md)。

## 33. 不迁移原因

分库分表、额外锁型和候补队列在当前规模没有足够业务收益；UV 只有 Demo；点赞 Outbox 会显著增加链路复杂度；VIP 规则缺少真实产品需求。它们的成本高于当前正确性、可靠性和求职展示收益。

## 34. 求职价值

- 必须保留：登录安全、缓存穿透/击穿治理、Lua 原子秒杀、一人一单、Outbox/Kafka 幂等、恢复对账、Flyway、测试。
- 加分项：生命周期 API、号段 ID、Sentinel 编排、可观测性、CI/Docker、Outbox 清理。
- 容易过度设计：无数据规模支撑的分库分表、无业务语义的多锁型、候补队列和全链路点赞 Outbox。
- 简历不要写：生产级、真实高可用、Kafka 故障恢复已验证、具体 QPS/P99、Sentinel 自动切换已验证、HyperLogLog UV 平台。
- 面试会深挖：Redis 扣成功后各失败点如何恢复、Outbox 重复投递、ACK 时机、唯一约束、SENT 重查、取消与补偿、号段耗尽/浪费、Sentinel 脑裂边界、指标如何告警。

## 35. 后续路线

1. 在有 Docker 的主机执行整栈启动、Kafka Producer→Broker→Consumer→DB E2E 和重复/异常消息测试。
2. 分别停止 Kafka、MySQL、Redis master 与 Consumer，记录 backlog、恢复时间、最终一致性和数据核对结果。
3. 安装 JMeter，按冷/热缓存和秒杀正常/并发/重复用户场景生成可追溯报告。
4. 推送当前分支，确认 GitHub Actions 实际运行结果；通过后再决定是否合并。
5. 用接近预期数据量的数据重新执行 EXPLAIN，并根据慢日志而不是猜测调整 Hikari、TTL jitter 和索引。

## 最终 Feature Matrix 摘要

| 能力 | Target | Mine | Result |
| --- | ---: | ---: | --- |
| L1 + L2 Cache | ✅ | ✅ | ≈ |
| Bloom + 空值 + DCL | ✅ | ✅ | ≈ |
| Lua Seckill + Handoff | ✅ | ✅ | ≈ |
| Kafka + Transactional Outbox | ✅ | ✅ | ≈，运行待验证 |
| Outbox 历史清理 | ❌ | ✅ | ⭐ |
| Subscription / Notification | ✅ | ✅ | ≈ |
| Waiting List | ❌ | ❌ | ≈ |
| Daily Top Buyer | ✅ | ✅ | ≈ |
| Sentinel Compose | ✅ | ✅ | ≈，运行待验证 |
| Actuator / Prometheus | ❌ | ✅ | ⭐ |
| Trace ID / MDC | ❌ | ✅ | ⭐ |
| GitHub Actions | ❌ | ✅ | ⭐，远程待验证 |
| Docker App Stack | ✅ | ✅ | ≈，本机待验证 |
| Kafka E2E / Fault Drill | ❌ | ❌ | NOT VERIFIED |
| 性能实测报告 | ❌ | ❌ | NOT VERIFIED |

完整 159 项 Feature Matrix 以 [HMDP_TARGET_GAP_MATRIX.md](HMDP_TARGET_GAP_MATRIX.md) 为准。
