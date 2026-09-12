# HMDP Remaining Gaps

本文件只记录 2026-09-12 真实资格复验后仍为 `PARTIAL`、`NOT VERIFIED` 或 `DEFECTIVE` 的 10 项。Kafka E2E、Consumer 重启、Kafka 停机恢复、MySQL Handoff 恢复和 JMeter 本机基线已经闭环，不再列为剩余缺口。最终没有 `MISSING` 项。

## Hikari 参数

目标实现：按消费者吞吐配置连接池。
我的状态：`PARTIAL`，仍使用 Spring Boot 默认池参数。
为什么没实现：本轮只有短时单实例基线，缺少长稳期间的连接等待、池利用率和 MySQL 容量数据，提前调参会变成猜测。
影响：高并发消费时可能出现连接等待，也可能因池过大挤压 MySQL。
是否建议实现：取得长稳和连接池指标后再调。
优先级：P1。
预计涉及文件：`application.yaml`。
验证方式：固定负载下联合观察 Hikari pending/active、Kafka backlog 与 MySQL 连接数。

## 统一 TTL jitter

目标实现：仅局部 TTL，没有统一策略。
我的状态：`PARTIAL`，分层 TTL 已有，随机抖动未统一。
为什么没实现：需要先识别会同批写入且同时过期的 key，不能全局随机化。
影响：极端批量预热后可能出现集中失效。
是否建议实现：仅对高批量缓存写路径增加小比例抖动。
优先级：P2。
预计涉及文件：`CacheClient`、商户/秒杀券缓存服务及测试。
验证方式：批量写入后统计 TTL 分布与回源峰值。

## Redis 故障读降级矩阵

目标实现：部分 best-effort。
我的状态：`PARTIAL`，Sentinel 切换、全停失败语义和恢复重连已实测，但不同接口尚未形成统一的 fail-open/fail-close 契约。
为什么没实现：秒杀写、登录、普通查询和缓存失效的正确策略不同。
影响：Redis 全停时接口虽然不会虚假成功，但目前仍以 HTTP 200 包装 `success:false`，客户端语义不够统一。
是否建议实现：建议按读取、登录、秒杀、缓存失效四类形成决策表并补契约测试。
优先级：P1。
预计涉及文件：缓存服务、登录服务、秒杀入口、异常处理和运维文档。
验证方式：隔离 Redis 全停/恢复，逐接口记录 HTTP 状态、业务结果、数据正确性和恢复时间。

## VIP / 积分容量倍率

目标实现：VIP 与高价值用户容量倍率。
我的状态：`PARTIAL`，已有活动/IP/用户限流和 backlog 自适应倍率，没有用户等级倍率。
为什么没实现：缺少明确产品规则，机械迁移会产生公平性与防刷漏洞。
影响：无法按用户价值做差异化准入。
是否建议实现：只有业务明确等级、积分和容量规则时实现。
优先级：P2。
预计涉及文件：限流属性、`SeckillRateLimitServiceImpl`、Lua 与测试。
验证方式：多等级用户并发测试，验证总容量不被倍率绕过。

## Blog 点赞跨存储一致性

目标实现：MySQL 与 Redis 弱一致，无 Outbox。
我的状态：`PARTIAL`，与目标相同。
为什么没实现：点赞允许短暂最终一致，增加 Outbox 的复杂度暂不划算。
影响：故障窗口可能出现计数与明细短暂不一致。
是否建议实现：业务要求强审计或结算时再做。
优先级：P2。
预计涉及文件：Blog Service、点赞事件 Outbox、Mapper、migration。
验证方式：DB/Redis 单点故障与重复事件回放。

## HyperLogLog UV 业务链

目标实现：测试 Demo。
我的状态：`PARTIAL`，同样只有 Demo。
为什么没实现：没有采集入口、口径、查询 API 和运营消费者。
影响：不能把现状作为 UV 统计功能展示。
是否建议实现：有真实访问统计需求时再补。
优先级：P2。
预计涉及文件：访问采集 Filter/Service、Controller、key 规范和测试。
验证方式：固定用户集写入，比较 PFCOUNT 误差与过期策略。

## 结构化日志

目标实现：普通文本日志。
我的状态：`PARTIAL`，已有 Trace ID，但不是统一 JSON 字段。
为什么没实现：没有确定日志采集栈与字段协议。
影响：集中检索和告警聚合成本较高。
是否建议实现：接入 ELK/Loki 前定义字段并实现。
优先级：P2。
预计涉及文件：Logback 配置、异常与 MQ 日志。
验证方式：日志解析测试并确认 traceId/orderId/eventId 可检索。

## Redis 6.2 + AOF + Docker Compose 验证

目标实现：Linux Redis 6.2 Compose 编排。
我的状态：`PARTIAL`；本轮在独立端口使用 Windows Redis 3.2.100 完成 1 主 2 从、3 Sentinel、master failover、客户端读写与重连，但临时数据节点为 `appendonly no`。
为什么没实现：当前机器没有 Docker、Podman 或 WSL，无法运行仓库中的 Linux Compose 栈。
影响：尚未证明镜像健康检查、Redis 6.2 行为、AOF 持久性以及全节点停止后的数据恢复。
是否建议实现：必须在发布前完成。
优先级：P1。
预计涉及文件：通常无需业务代码修改；必要时修正 `compose.yaml`、`docker/redis` 和演练文档。
验证方式：Linux/Docker 启动仓库 Compose，执行 master failover、全停、AOF 恢复及数据一致性核对。

## 限流 HTTP 429 契约

目标实现：限流异常映射为 HTTP 429。
我的状态：`DEFECTIVE`；本轮 15,161 次 `SeckillRateLimitException` 均被全局处理为 HTTP 200，JMeter 因此显示 0 HTTP 错误。
为什么没实现：本轮按要求冻结业务功能，只记录缺陷，不改变接口契约。
影响：客户端、网关和监控无法通过 HTTP 状态码识别限流。
是否建议实现：建议作为下一项 P0 修复，并补 MVC/集成测试。
优先级：P0。
预计涉及文件：全局异常处理、限流异常映射及相应测试。
验证方式：触发限流后同时断言 HTTP 429、业务错误码和 Micrometer 计数。

## 生产资格

目标实现：没有生产证据。
我的状态：`NOT VERIFIED` / `NO-GO`。
为什么没实现：本轮完成的是本地短时真实依赖验证；远程 CI 修复尚未取得绿灯，也没有 Linux Compose、长稳、备份恢复、安全审计和发布回滚记录。
影响：不能宣称生产级、高可用或已承载真实流量。
是否建议实现：上线前必须完成独立资格门禁。
优先级：P3（不影响源码对标，但影响上线声明）。
预计涉及文件：部署、监控、告警、备份恢复、容量与发布文档。
验证方式：CI 绿灯、预生产 Linux Compose 演练、长稳与容量测试、备份恢复、安全审计和发布回滚验证。
