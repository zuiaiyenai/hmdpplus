# HMDP Target Gap Matrix

> 审计日期：2026-09-12  
> 目标仓库：`yyx758/hmdp-plus`，`master@191e3a2a978901612adfb391b30339d76aea8109`（2026-08-31T14:42:52+08:00）  
> 我的仓库：`zuiaiyenai/hmdpplus`，`feature/hmdp-plus-migration@dfc2f1f23d03c4a5d8586a4643e345e22b6b93c5`（初始快照）  
> 最终实现快照：`feature/hmdp-plus-migration@4558bba`（最终文档提交前的代码快照）
> 证据口径：代码/配置存在不等于真实依赖、故障、性能或生产验证通过。

## Target Gap Summary

本矩阵把能力拆成 159 个可判定项。状态统计由下表逐行计算；`MATCHED`、`EQUIVALENT`、`BETTER` 计为完成，`PARTIAL` 按 50% 计入初始功能对齐率，`NOT VERIFIED`、`MISSING`、`DEFECTIVE` 不计入。

| 状态 | 数量 |
| --- | ---: |
| MATCHED | 102 |
| EQUIVALENT | 13 |
| BETTER | 12 |
| PARTIAL | 11 |
| MISSING | 8 |
| NOT VERIFIED | 13 |
| DEFECTIVE | 0 |

初始真实功能对齐率：**83.3%**。运行资格不并入静态功能完成率，单独列为 `NOT VERIFIED`。

最终复审仍按相同 159 项逐行计算：`MATCHED 108`、`EQUIVALENT 13`、`BETTER 22`、`PARTIAL 7`、`MISSING 0`、`NOT VERIFIED 9`、`DEFECTIVE 0`，最终证据加权对齐率为 **92.1%**。下表状态已更新为最终复审结果；初始统计保留用于前后对照。

## 完整功能矩阵

| 功能 | 目标仓库 | 我的项目 | 状态 | 目标实现 | 我的实现 | 缺失内容 | 是否需要迁移 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Java 版本 | Java 8 | Java 8 | MATCHED | `pom.xml` | `pom.xml` | 无 | 否 | P3 |
| Spring Boot 版本 | 2.3.12.RELEASE | 2.3.12.RELEASE | MATCHED | `pom.xml` | `pom.xml` | 无 | 否 | P3 |
| Maven 单模块 | 单模块 JAR | 单模块 JAR | MATCHED | `pom.xml` | `pom.xml` | 无 | 否 | P3 |
| 包结构 | `com.hmdp` | `com.hmdp` | MATCHED | `src/main/java/com/hmdp` | 同路径 | 无 | 否 | P3 |
| 环境变量配置 | 大部分可覆盖 | DB/Redis/Kafka 可覆盖 | MATCHED | `application.yaml` | `application.yaml` | 无 | 否 | P1 |
| Hikari 参数 | 有明确池配置 | 使用 Boot 默认 | PARTIAL | `application.yaml` | `application.yaml` | 缺少面向消费者的池参数 | 是 | P1 |
| Redis 连接池 | 32/16 | 10/10 | EQUIVALENT | 目标偏高并发 | 我的保守默认 | 需压测后定值 | 否 | P3 |
| 全局异常处理 | 有 | 有且隐藏意外异常细节 | BETTER | `WebExceptionAdvice` | 同类 + 安全修复 | 无 | 否 | P0 |
| 业务异常消息 | 有 | 保留业务消息 | MATCHED | `BusinessException` | 同类 | 无 | 否 | P0 |
| 短信验证码 | Redis | Redis | MATCHED | `UserServiceImpl` | 同类 | 无 | 否 | P0 |
| 验证码冷却 | 有 | 有 | MATCHED | `UserServiceImpl` | 同类 | 无 | 否 | P1 |
| 验证码一次性消费 | 有 | Lua/原子删除语义 | EQUIVALENT | 登录消费验证码 | 登录消费验证码 | 无 | 否 | P0 |
| 登录 Token | Redis UUID | Redis UUID | MATCHED | `UserServiceImpl` | 同类 | 无 | 否 | P0 |
| Token 刷新 | 拦截器刷新 TTL | 刷新 TTL + 用户 Token 索引 | BETTER | `RefreshTokenInterceptor` | 同类 | 无 | 否 | P0 |
| Logout | 删除 Token | compare-delete 并维护索引 | BETTER | `UserServiceImpl` | 同类 | 无 | 否 | P0 |
| 单用户 Token | 有索引 | 有索引 | MATCHED | Redis 索引 | Redis 索引 | 无 | 否 | P1 |
| ThreadLocal 清理 | 拦截器 afterCompletion | 拦截器 afterCompletion | MATCHED | `UserHolder` | `UserHolder` | 无 | 否 | P0 |
| 登录拦截 | 有 | 有且匿名面更窄 | BETTER | `MvcConfig` | `MvcConfig` | 无 | 否 | P0 |
| 管理接口鉴权 | 部分路径放行较宽 | 商户/券/上传写接口受保护 | BETTER | `MvcConfig` | `MvcConfig` | 无 | 否 | P0 |
| 上传路径边界 | 有上传删除 | 归一化并限制根目录 | BETTER | `UploadController` | `UploadController` | 无 | 否 | P0 |
| 密码存储 | BCrypt/兼容逻辑 | 密码编码与旧数据兼容 | EQUIVALENT | `PasswordEncoder` | 同类 | 无 | 否 | P0 |
| Caffeine L1 | 商户/秒杀券 | 商户/秒杀券 | MATCHED | `ShopLocalCache` 等 | 同类 | 无 | 否 | P1 |
| Redis L2 | 有 | 有 | MATCHED | `ShopCacheServiceImpl` | 同类 | 无 | 否 | P1 |
| Cache Aside | 有 | 有 | MATCHED | 查询/更新路径 | 查询/更新路径 | 无 | 否 | P1 |
| 空值缓存 | 有 | 有 | MATCHED | 短 TTL | 短 TTL | 无 | 否 | P1 |
| Bloom Filter | 商户/秒杀券 | 商户/秒杀券 | MATCHED | Bloom 组件 | Bloom 组件 | 无 | 否 | P1 |
| 缓存穿透治理 | Bloom + 空值 | Bloom + 空值 | MATCHED | 缓存服务 | 缓存服务 | 无 | 否 | P1 |
| 缓存击穿治理 | Redisson + DCL | Redisson + DCL | MATCHED | 缓存服务 | 缓存服务 | 无 | 否 | P1 |
| 缓存雪崩治理 | 分层 TTL | 分层 TTL | MATCHED | 配置化 TTL | 配置化 TTL | 缺真实抖动分布证明 | 否 | P2 |
| TTL jitter | 未形成统一实现 | 未形成统一实现 | PARTIAL | 局部 TTL | 局部 TTL | 可统一加入小随机量 | 可选 | P2 |
| 逻辑过期 | 秒杀券使用 | 秒杀券使用 | MATCHED | `SeckillVoucherCacheService` | 同类 | 无 | 否 | P1 |
| 异步重建 | 有线程池 | 有线程池 | MATCHED | 重建 executor | 重建 executor | 无 | 否 | P1 |
| Double Check | 有 | 有 | MATCHED | 锁后二次读 | 锁后二次读 | 无 | 否 | P1 |
| Redisson 重建锁 | 有 | 有 | MATCHED | `RedissonConfig` | 同类 | 无 | 否 | P1 |
| 热点预热 | 启动初始化 | 启动初始化 | MATCHED | Initializer | Initializer | 无 | 否 | P1 |
| 跨实例 L1 失效 | Outbox + Kafka | Outbox + Kafka | MATCHED | cache invalidation 链路 | 同类 | 无 | 否 | P1 |
| 损坏缓存自愈 | 基础处理 | 删除 `{}`/错 ID/坏 JSON 后回源 | BETTER | `ShopCacheServiceImpl` | 同类增强 | 无 | 否 | P0 |
| Redis 故障读降级 | 部分 best-effort | 部分 best-effort | PARTIAL | 失效路径容错 | 失效路径容错 | 缺统一降级矩阵 | 是 | P1 |
| 普通 Redis 锁 | 有 | 有 | MATCHED | `SimpleRedisLock` | 同类 | 无 | 否 | P1 |
| 锁安全释放 | compare-delete Lua | compare-delete Lua | MATCHED | Lua | Lua | 无 | 否 | P0 |
| 注解式锁 | 有 | 有 | MATCHED | `CacheConsistencyLock` | 同类 | 无 | 否 | P2 |
| AOP 锁 | 有 | 有 | MATCHED | Aspect | Aspect | 无 | 否 | P2 |
| SpEL Key | 有 | 有 | MATCHED | Aspect | Aspect | 无 | 否 | P2 |
| 公平锁 | 无核心业务使用 | 无核心业务使用 | EQUIVALENT | 不机械增加 | 不机械增加 | 无业务收益 | 否 | P3 |
| 读写锁 | 无核心业务使用 | 无核心业务使用 | EQUIVALENT | 不机械增加 | 不机械增加 | 无业务收益 | 否 | P3 |
| Watchdog | 可由无 lease 锁触发 | 显式 lease 为主 | EQUIVALENT | Redisson | Redisson | 不能笼统宣传 | 否 | P3 |
| 秒杀活动状态 | Lua 元数据 | Lua 元数据 | MATCHED | `seckill.lua` | `seckill.lua` | 无 | 否 | P0 |
| 秒杀时间窗口 | Lua 校验 | Lua 校验 | MATCHED | `seckill.lua` | `seckill.lua` | 无 | 否 | P0 |
| 一人一单 | Redis Set + DB 唯一约束 | 同方案 | MATCHED | Lua/唯一索引 | Lua/唯一索引 | 无 | 否 | P0 |
| 前置 Token 发放 | 有 | 有 | MATCHED | Token Service | Token Service | 无 | 否 | P1 |
| 一次性 Token 校验 | Lua 内消费 | Lua 内消费 | MATCHED | `seckill.lua` | `seckill.lua` | 无 | 否 | P0 |
| Token 重放防护 | 已消费不可复用 | 已消费不可复用 | MATCHED | Lua | Lua | 无 | 否 | P0 |
| IP 限流 | Lua Token Bucket | Lua Token Bucket | MATCHED | RateLimitService | RateLimitService | 无 | 否 | P1 |
| 用户限流 | Lua Token Bucket | Lua Token Bucket | MATCHED | RateLimitService | RateLimitService | 无 | 否 | P1 |
| 活动限流 | 策略桶 | 活动/场景桶 | EQUIVALENT | Lua policy key | Lua activity key | 无 | 否 | P1 |
| 动态阈值 | backlog 自适应 | backlog 自适应 | MATCHED | PressureService | PressureService | 无 | 否 | P1 |
| VIP/高价值优先级 | 有倍率 | 我的版本无用户倍率 | PARTIAL | 用户倍率 | 总体/场景容量 | 缺 VIP/积分倍率 | 可选 | P2 |
| 限流 HTTP 429 | 异常映射 | 异常映射 | MATCHED | Advice | Advice | 无 | 否 | P1 |
| 可信代理解析 | 默认关闭转发头 | 白名单代理才信任 | BETTER | ClientIpResolver | ClientIpResolver + trusted proxies | 无 | 否 | P0 |
| Lua 原子扣库存 | 有 | 有 | MATCHED | `seckill.lua` | `seckill.lua` | 无 | 否 | P0 |
| Lua 订单 ID | 参数传入 | 参数传入 | MATCHED | Java 发号 | Java 发号 | 无 | 否 | P0 |
| Lua Handoff | ZSet | ZSet | MATCHED | `seckill.lua` | `seckill.lua` | 无 | 否 | P0 |
| Lua accepted marker | 有 | 有 | MATCHED | `seckill.lua` | `seckill.lua` | 无 | 否 | P0 |
| Lua 补偿 | 取消/回滚脚本 | 取消/回滚脚本 | MATCHED | Lua scripts | Lua scripts | 无 | 否 | P0 |
| Redis 结果未知语义 | 返回待确认订单 ID | 返回待确认订单 ID | MATCHED | Service | Service | 无 | 否 | P0 |
| HTTP 异步受理 | 不同步写 MySQL | 不同步写 MySQL | MATCHED | Service | Service | 无 | 否 | P0 |
| 订单状态查询 | Controller 内多个查询 | 独立生命周期 Controller/Service | BETTER | VoucherOrderController | Lifecycle Controller | 无 | 否 | P1 |
| 取消订单 | 有 | 有且生命周期幂等 | BETTER | Service cancellation | Lifecycle Service | 无 | 否 | P0 |
| Handoff 到 Outbox | 有 | 有 | MATCHED | HandoffRelay | HandoffRelay | 无 | 否 | P0 |
| Outbox 后删 Handoff | 有 | 有 | MATCHED | 提交后 Lua | 提交后 Lua | 无 | 否 | P0 |
| 坏 Handoff 隔离 | 有 | 有 | MATCHED | quarantine | quarantine | 无 | 否 | P0 |
| Handoff 临时错误保留 | 有 | 有 | MATCHED | retry/backoff | retry/backoff | 无 | 否 | P0 |
| Producer acks=all | 有 | 有 | MATCHED | Kafka 配置 | Kafka 配置 | 无 | 否 | P0 |
| Producer 幂等 | 有 | 有 | MATCHED | enable.idempotence | 同配置 | 无 | 否 | P0 |
| Producer 超时 | 有 | 有 | MATCHED | timeout 配置 | timeout 配置 | 无 | 否 | P1 |
| Producer 重试 | 有 | 有 | MATCHED | retries | retries | 无 | 否 | P1 |
| Outbox 指数退避 | 有 | 有 | MATCHED | Relay | Relay | 无 | 否 | P0 |
| Outbox 行租约 | 有 | 有 | MATCHED | Mapper/Relay | Mapper/Relay | 无 | 否 | P0 |
| Outbox SENT 重查 | 有 | 有 | MATCHED | sent recheck | sent recheck | 无 | 否 | P0 |
| Outbox 清理 | 无明确归档清理闭环 | 仅删除超期 COMPLETED，分批且有单轮上限 | BETTER | 状态保留 | `SeckillOrderOutboxCleanupJob` + V4 索引 | 无 | 否 | P1 |
| Outbox dead record | DLT/失败状态 | DLT/失败状态 | EQUIVALENT | Kafka DLT | Kafka DLT + quarantine | 无 | 否 | P0 |
| Kafka Topic 持久化 | Broker 默认磁盘日志 | Broker 默认磁盘日志 | MATCHED | Compose Kafka | Compose Kafka | 无 | 否 | P1 |
| DLT | 有 | 有 | MATCHED | DLT topic | DLT topic | 无 | 否 | P0 |
| Consumer 手动 ACK | 有 | 有 | MATCHED | manual_immediate | manual_immediate | 无 | 否 | P0 |
| Consumer 重试 | 有 | 有 | MATCHED | error handler | error handler | 无 | 否 | P0 |
| Consumer 幂等 | 唯一键/批量校验 | 唯一键/批量校验 | MATCHED | persistence service | persistence service | 无 | 否 | P0 |
| Consumer 批量落库 | 有 | 有 | MATCHED | batch listener | batch listener | 无 | 否 | P1 |
| Duplicate message | 不重复扣库 | 不重复扣库 | MATCHED | unique + inserted count | 同方案 | 无 | 否 | P0 |
| Poison message | DLT | DLT + 同步确认 recoverer | BETTER | DLT | `SynchronousDeadLetterPublishingRecoverer` | 无 | 否 | P0 |
| Kafka Broker E2E | 目标源码有，当前环境未跑 | 当前环境未跑 | NOT VERIFIED | 需真实 broker | 需真实 broker | Docker/Kafka 不可用 | 是 | P0 |
| Consumer 重启恢复 | 设计存在 | 设计存在 | NOT VERIFIED | offset/ACK | offset/ACK | 缺真实演练 | 是 | P1 |
| Kafka 暂停恢复 | Outbox 应保留 | Outbox 应保留 | NOT VERIFIED | 设计证据 | 设计证据 | 缺真实演练 | 是 | P0 |
| Redis/MySQL 库存一致性 | 对账与补偿 | 对账与补偿 | MATCHED | Reconciliation | Reconciliation | 无 | 否 | P0 |
| accepted 恢复 | 定时恢复 | 定时恢复 | MATCHED | RecoveryService | RecoveryService | 无 | 否 | P0 |
| 启动库存恢复 | Initializer | Initializer | MATCHED | StockInitializer | StockInitializer | 无 | 否 | P0 |
| 定时全量对账 | 有 | 有 | MATCHED | ReconciliationService | 同类 | 无 | 否 | P1 |
| MySQL 故障 Handoff 保留 | 设计存在 | 设计存在 | NOT VERIFIED | Handoff | Handoff | 缺真实停机演练 | 是 | P0 |
| Redis 重启恢复 | Sentinel/初始化/对账 | Sentinel 编排 + 初始化 + 对账 | MATCHED | Sentinel Compose | 1 主 2 从 + 3 Sentinel Compose | 实际切换演练另列未验证 | 否 | P1 |
| Redis 数据丢失恢复 | 重建元数据/投影 | 重建元数据/投影 | MATCHED | Synchronizer | Synchronizer | 无 | 否 | P0 |
| 单 Redis Compose | 有 | 有 | MATCHED | compose 变体 | `compose.yaml` | 无 | 否 | P1 |
| Redis Sentinel Compose | 3 节点 + 3 Sentinel | 3 节点 + 3 Sentinel | MATCHED | Docker configs/Compose | `compose.yaml` + `docker/redis` | 无静态能力缺口 | 否 | P1 |
| 订阅 | 有 | 有 | MATCHED | SubscriptionService | 同类 | 无 | 否 | P2 |
| 取消订阅 | 有 | 有 | MATCHED | unsubscribe | unsubscribe | 无 | 否 | P2 |
| 开场提醒 | 有 | 有 | MATCHED | ReminderService | 同类 | 无 | 否 | P2 |
| 通知去重 | Lua | Lua | MATCHED | notification Lua | 同脚本 | 无 | 否 | P2 |
| 通知频控/保留 | 有 | 有 | MATCHED | MarketingProperties | 同类 | 无 | 否 | P2 |
| 候补队列 | 未实现 | 未实现 | EQUIVALENT | 无 | 无 | 目标也无真实能力 | 否 | P3 |
| 候补晋升 | 未实现 | 未实现 | EQUIVALENT | 无 | 无 | 目标也无真实能力 | 否 | P3 |
| 每日 Top 买家 | ZSet | ZSet | MATCHED | TopBuyerService | 同类 | 无 | 否 | P2 |
| Top Buyer TTL | 有 | 有 | MATCHED | retention days | retention days | 无 | 否 | P2 |
| Top Buyer API | 有 | 有 | MATCHED | VoucherController | VoucherController | 无 | 否 | P2 |
| Feed 推模式 | 有 | 有 | MATCHED | BlogServiceImpl | 同类 | 无 | 否 | P1 |
| Feed 滚动分页 | max+offset | max+offset | MATCHED | ZSet | ZSet | 无 | 否 | P1 |
| Feed 同分边界 | 有测试 | 有测试 | MATCHED | Blog tests | Blog tests | 无 | 否 | P1 |
| Blog 点赞 | ZSet | ZSet | MATCHED | BlogService | BlogService | 无 | 否 | P1 |
| Blog 取消点赞 | 有 | 有 | MATCHED | BlogService | BlogService | 无 | 否 | P1 |
| 点赞跨存储一致性 | 无 Outbox | 无 Outbox | PARTIAL | 弱一致窗口 | 弱一致窗口 | 可选 Outbox | 可选 | P2 |
| 关注/取关 | DB + Redis Set | DB + Redis Set | MATCHED | FollowService | FollowService | 无 | 否 | P1 |
| 共同关注 | SINTER | SINTER | MATCHED | FollowService | FollowService | 无 | 否 | P1 |
| GEO 查询 | GEOSEARCH | GEOSEARCH | MATCHED | ShopService | ShopService | 无 | 否 | P1 |
| GEO 启动重建 | 有 | 有 | MATCHED | GeoInitializer | GeoInitializer | 无 | 否 | P1 |
| GEO 坐标缺失降级 | DB 分页 | DB 分页 | MATCHED | ShopService | ShopService | 无 | 否 | P1 |
| BitMap 签到 | 有 | 有 | MATCHED | UserService | UserService | 无 | 否 | P1 |
| 连续签到 | 有 | 有 | MATCHED | bitfield | bitfield | 无 | 否 | P1 |
| 补签 | 有 | 有 | MATCHED | UserService | UserService | 无 | 否 | P2 |
| HyperLogLog Demo | 测试 Demo | 测试 Demo | PARTIAL | ApplicationTests | ApplicationTests | 无业务链路 | 可选 | P2 |
| UV Controller/Service | 无 | 无 | EQUIVALENT | 无 | 无 | 目标也无 | 否 | P3 |
| 全局 ID | DB 号段 + 预取 | DB 号段 + 预取/Redis 显式回退 | BETTER | Segment generator | Segment + fallback mode | 无 | 否 | P1 |
| ID 唯一性 | DB 高水位 | DB 高水位 | MATCHED | IdSegment | IdSegment | 无 | 否 | P0 |
| ID 重启 | 新号段 | 新号段 | MATCHED | allocator | allocator | 无 | 否 | P0 |
| 分库分表 | 未实现 | 未实现 | EQUIVALENT | 无 ShardingSphere | 无 ShardingSphere | 对校招项目收益不足 | 否 | P3 |
| 订单唯一索引 | 有 migration | 有 migration | MATCHED | Flyway | Flyway | 无 | 否 | P0 |
| 订单复合索引 | 有 | 有 | MATCHED | V8 | V3 合并迁移 | 无 | 否 | P1 |
| Outbox 索引 | 有 | 有 | MATCHED | V5-V8 | V2/V3 合并迁移 | 无 | 否 | P0 |
| Flyway | V2-V9 拆分 | V2-V3 合并 | EQUIVALENT | 细粒度历史 | 等价 schema 演进 | 不应复制已执行版本号 | 否 | P0 |
| EXPLAIN 慢查询验证 | 未保存完整证据 | 本轮验证 Outbox 清理/投递及订单唯一性查询 | BETTER | 需真实数据 | MySQL 5.7 EXPLAIN 使用 cleanup/dispatch/order 索引 | 大数据量分布仍需生产前复核 | 否 | P1 |
| Actuator | 无 | health/info/metrics | BETTER | 目标未提供 | `spring-boot-starter-actuator` | 无 | 否 | P1 |
| Prometheus | 无 | `/actuator/prometheus` | BETTER | 目标未提供 | Micrometer Prometheus registry | 无 | 否 | P1 |
| 业务 Metrics | 少量日志 | backlog、准入倍率、秒杀结果指标 | BETTER | 无成熟实现 | `MetricsConfig`/`SeckillMetricsAspect` | 告警规则尚未部署 | 否 | P1 |
| Trace ID/MDC | 无统一链路 | 请求 Trace ID + MDC 清理 | BETTER | 无 | `RequestTraceFilter` | 无 | 否 | P1 |
| 结构化日志 | 普通文本日志 | 普通文本日志 | PARTIAL | Logback 默认 | Logback 默认 | 缺统一字段 | 可选 | P2 |
| JMeter 场景 | 有 | 有 | MATCHED | `load-tests/jmeter` | 同目录 | 无 | 否 | P1 |
| 缓存基准脚本 | 有 | 有 | MATCHED | benchmark script | 同脚本 | 无 | 否 | P1 |
| QPS/P50/P95/P99 | 有脚本无本轮结果 | 有脚本无本轮结果 | NOT VERIFIED | 需真实运行 | 需真实运行 | 当前无 Docker/JMeter | 是 | P1 |
| Redis 故障注入 | 有方案 | 有方案 | NOT VERIFIED | Compose stop/start | 文档方案 | 本机无 Docker | 是 | P1 |
| MySQL 故障注入 | 有方案 | 有方案 | NOT VERIFIED | Compose stop/start | 文档方案 | 本机无 Docker | 是 | P0 |
| Kafka 故障注入 | 有方案 | 有方案 | NOT VERIFIED | Compose stop/start | 文档方案 | 本机无 Docker | 是 | P0 |
| GitHub Actions CI | 无 | MySQL/Redis/Kafka services + Java 8 Maven test | BETTER | 目标缺失 | `.github/workflows/ci.yml` | 未在本分支远程触发 | 否 | P1 |
| Maven 自动构建 | 手工 | push/PR 自动测试 | BETTER | 可运行 Maven | GitHub Actions | 无代码能力缺口 | 否 | P1 |
| Dockerfile | 有 | 有 | MATCHED | 多阶段 Java 8 镜像 | 多阶段 Java 8 镜像 | 无 | 否 | P1 |
| App Compose | 有 app service | app + MySQL + Sentinel Redis + Kafka | MATCHED | app+依赖 | 完整依赖与健康顺序 | 无静态能力缺口 | 否 | P1 |
| MySQL Compose | 有 | 有 | MATCHED | MySQL 5.7 | MySQL 5.7 | 无 | 否 | P1 |
| Redis Compose | 有 | 有 | MATCHED | Redis 6.2 | Redis 6.2 | 无 | 否 | P1 |
| Kafka Compose | 有 | 有 | MATCHED | Kafka 3.7.1 | Kafka 3.7.1 | 无 | 否 | P1 |
| Healthcheck | 各服务有 | app 与全部依赖均有 | MATCHED | app 依赖链 | Compose healthcheck/depends_on | 无静态能力缺口 | 否 | P1 |
| 本轮 Maven 全量测试 | 固定目标源码未重跑 | 175 tests 全绿 | MATCHED | 历史目标测试证据 | MySQL/Redis 真实依赖回归 | Kafka 由独立 E2E 项约束 | 否 | P0 |
| 真实 MySQL | 固定目标源码未重跑 | MySQL 5.7.26 已验证 | BETTER | 仅源码证据 | Flyway V4、Mapper 集成测试、EXPLAIN | 非生产数据规模 | 否 | P0 |
| 真实 Redis | 固定目标源码未重跑 | 真实 Redis Lua 并发测试通过 | BETTER | 仅源码证据 | 40 并发一人一单/库存/Token 原子性 | Sentinel 切换另列未验证 | 否 | P0 |
| 生产资格 | 未证明 | 未证明 | NOT VERIFIED | 无生产证据 | 无生产证据 | 需要长期运行证据 | 否 | P3 |

## 双方秒杀主链

目标仓库：`HTTP -> Controller 限流 -> 一次性 Token -> Lua -> Redis Handoff -> MySQL Outbox -> Kafka -> 批量 Consumer -> MySQL`。

我的项目：`HTTP -> Login/限流拦截器 -> 一次性 Token -> Lua -> Redis Handoff -> MySQL Outbox -> Kafka -> 批量 Consumer -> MySQL -> Lifecycle 查询/取消/恢复`。

两条链路在可靠投递能力上等价；我的项目把入口限流下沉为 MVC 拦截器，并增加独立生命周期 API，不能为了类名一致而降级。

## 初始迁移计划

1. Phase 0：固定 SHA、建立矩阵和证据口径。
2. Phase 1：修复测试环境可复现性，补齐目标独有专项测试中尚未被覆盖的断言。
3. Phase 2：增加 GitHub Actions CI，使用真实 MySQL/Redis 服务运行 Maven 测试。
4. Phase 3：增加应用 Dockerfile、app Compose 与健康检查。
5. Phase 4：增加 Actuator/Prometheus、请求 traceId 和秒杀/Outbox 指标。
6. Phase 5：增加 Outbox 历史清理与可观测告警边界。
7. Phase 6：在环境可用时执行 Kafka E2E、停机恢复、EXPLAIN 与压测。
8. Phase 7：重新索引双方源码，生成最终报告和仅含未关闭项的清单。

## 不迁移决策

- 不新增 ShardingSphere：目标 `yyx758/hmdp-plus` 本身也未使用；对当前 Java 初级求职项目，先讲清单库一致性与可靠消息更有价值。
- 不新增公平锁、读写锁：当前业务没有相应互斥模型，增加锁类型只会扩大测试面。
- 不实现候补队列：目标仓库没有真实实现，当前优先级低于可靠性、CI 和可观测性。
- 不把 HLL Demo 包装成 UV 业务：没有 Controller/Service/采集/查询闭环。
- 不复制目标 V2-V9 migration：我的数据库已使用 V2/V3，复制历史版本会造成 Flyway 校验冲突；后续新增 schema 使用新的版本号。
