# HMDP Plus 迁移变更记录

> 目标：`D:\code\heimadianping\hm-dianping`
> 参考：`D:\code\hmdp-plus`（只读）
> 分支：`feature/hmdp-plus-migration`

## 变更原则

- 不覆盖整个仓库，不改写 `main/master` 历史。
- 只迁移能改善正确性、可靠性、可维护性或求职讲解价值的能力。
- 参考项目存在的能力不自动等于目标项目应该采用；Redis Stream、Java 17 多模块、ShardingSphere 未机械复制。
- 每项结论区分自动化、真实依赖、真实 API 和生产证明。

## Phase 0：安全基线与差异分析

- **内容**：确认 Git 根目录、目标/参考角色、独立迁移分支，按文件和方法比较两个独立历史仓库。
- **修改**：无整仓覆盖；保留目标项目已有业务。
- **验证**：Git 状态、分支、最近提交、参考仓库只读检查。
- **提交**：基线历史包含 `61af454`、`81674f1`；后续能力汇总于 `f71769f`。

## Phase 1：基础设施与 Flyway

- **新增/修改**：`pom.xml`、`application.yaml`、`compose.yaml`、`V2__create_outbox_infrastructure.sql`、`V3__complete_stage_7_to_9_infrastructure.sql`。
- **原因**：增加 Kafka、Flyway、Caffeine、AOP、Outbox 表、号段表和查询索引。
- **验证**：Flyway 在本地 MySQL 5.7 识别 3 个 migration，版本 3；迁移前有 SQL 备份。
- **提交**：`f71769f feat: 完善点评业务与秒杀高可用能力`。

## Phase 2：Lua 原子秒杀入口

- **新增/修改**：`VoucherOrderServiceImpl`、`seckill.lua`、访问令牌与限流 Lua、相关测试。
- **原因**：一次完成时间、库存、一人一单、令牌消费和 Handoff 写入；结果未知时禁止盲目回滚。
- **验证**：真实 Redis 并发测试曾验证同用户只成功一次、库存边界和坏令牌不改变状态；最新构建相关测试继续通过。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 3：Redis Handoff 与 Transactional Outbox

- **新增/修改**：`SeckillOrderHandoffService`、`SeckillOrderHandoffRelay`、`SeckillOrderOutboxBatchWriter`、Outbox 实体/Mapper、清理/隔离 Lua。
- **原因**：MySQL 不可用时保留已受理订单；只在 Outbox 事务提交后清除 Handoff；坏事件隔离、临时错误重试。
- **验证**：Relay、Mapper 集成、冲突拆批和删除时机测试通过。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 4：Kafka 批量落库

- **新增/修改**：`SeckillOrderKafkaConfig`、Producer、Consumer、`SeckillOrderOutboxRelay`、`VoucherOrderPersistenceServiceImpl`、DLT Recoverer。
- **原因**：HTTP 线程不直接写订单；用 Outbox 行租约、幂等生产、手动 ACK、批量事务和唯一键处理至少一次投递。
- **验证**：自动化测试通过；Kafka Broker E2E **NOT VERIFIED**。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 5：生命周期、恢复、取消与对账

- **新增/修改**：`SeckillOrderLifecycleService`、`SeckillAcceptedOrderRecoveryService`、`SeckillOrderReconciliationService`、生命周期 Controller/Mapper、取消补偿 Lua。
- **原因**：区分已受理、处理中、成功、取消和失败；避免把异步处理中误报为失败。
- **验证**：生命周期、恢复、对账和补偿单元测试通过；真实停机恢复演练 **NOT VERIFIED**。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 6：限流与营销能力

- **新增/修改**：`SeckillRateLimitServiceImpl`、`ClientIpResolver`、令牌桶 Lua、订阅/提醒/通知/Top 买家服务与测试。
- **原因**：活动、IP、用户三层联合准入；补充可展示的营销闭环。
- **验证**：令牌桶原子性、代理地址、订阅通知等自动化测试通过。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 7：商户与秒杀券多级缓存

- **新增/修改**：Caffeine LocalCache、Bloom Filter、`ShopCacheServiceImpl`、`SeckillVoucherCacheService`、Redisson 配置、缓存失效 Outbox/Kafka。
- **原因**：降低缓存穿透、热点重建和多实例 L1 陈旧风险。
- **验证**：本地缓存、Bloom、双重检查锁、失效发布/消费测试通过；缓存 A/B 性能数据 **NOT VERIFIED**。
- **提交**：基础能力 `f71769f`；损坏商户缓存自愈 `95de7a9`。

## Phase 8：数据库号段 ID

- **新增/修改**：`IdSegmentAllocator`、`DatabaseSegmentOrderIdGenerator`、`tb_id_segment` migration。
- **原因**：从 JVM 本地号段发号，20% 阈值预取，减少每单访问 Redis/数据库；保留显式 Redis 模式回退。
- **验证**：号段锁定、高水位推进、预取与生成器测试通过。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 9：社交、GEO、BitMap 与账户功能合并

- **新增/修改**：Blog、Follow、User、Shop、Voucher 相关 Service/Controller/DTO/Test；`ShopGeoIndexInitializer`。
- **原因**：保留已有点赞、关注、Feed、签到/补签，补齐 GEO、资料、密码和管理能力。
- **验证**：Feed 同分 offset、关注交集、GEO 顺序、签到连续天数、资料与密码测试通过。
- **提交**：能力最终汇总于 `f71769f`。

## Phase 10：安全与会话生命周期修复

| Commit | 变更 | 主要文件 | 验证 |
|---|---|---|---|
| `613d287` | 收紧券/上传接口，上传删除做归一化根边界校验 | `MvcConfig`、`UploadController` | 访问控制与上传测试 |
| `f8c28e2` | 保护商户管理接口 | `MvcConfig` | `MvcAccessControlTest` |
| `a5ac2cb` | 保留业务异常消息 | `WebExceptionAdvice` | `WebExceptionAdviceTest` |
| `27d996c` | 验证码冷却/一次性消费、单用户单 Token、续期索引和安全退出 | `UserServiceImpl`、`RefreshTokenInterceptor`、`RedisConstants` | 登录、退出、Interceptor 测试 |
| `95de7a9` | 损坏/空对象/ID 不匹配商户缓存删除并回源 | `ShopCacheServiceImpl` | 缓存自愈测试 + 真实 `GET /shop/1` |

## Phase 11：最终构建与真实运行

- **构建**：`mvn clean package`，167 tests，0 failure/error/skip；生成可执行 JAR。
- **依赖**：MySQL 5.7、Redis 6379 实际连接；Flyway 版本 3。
- **启动**：JAR 在 8081 启动成功，验收进程已停止。
- **接口**：损坏缓存自愈 PASS；匿名商户新增返回 401 PASS；验证码首次发送和立即重发冷却 PASS。
- **边界**：完整验证码登录因一次读取错误验证码未完成；Kafka Broker、JMeter、故障演练均 **NOT VERIFIED**。

## Phase 12：文档交付

- **新增**：`docs/HMDP_PLUS_MIGRATION_REPORT.md`、`docs/HMDP_INTERVIEW_GUIDE.md`、`docs/HMDP_CHANGELOG.md`。
- **原因**：记录 33 个要求章节、真实代码路径、方案取舍、面试答案、测试证据和未验证项。
- **验证**：标题、围栏、文件引用、夸大表述、`git diff --check` 和最终 Git 状态检查。
- **提交**：见包含本文件的文档提交。

## 文件删除记录

本轮收尾没有删除业务源码。历史迁移中仅移除/替换由当前秒杀链路淘汰的旧回滚脚本；未删除用户已有博客、关注、签到或本地配置数据。

## 证据总览

| 证据 | 状态 |
|---|---|
| 源码与配置 | VERIFIED |
| 167 个 Maven 测试 | PASS |
| MySQL/Redis/Flyway/JAR 启动 | PASS（本地） |
| 部分真实 API | PASS |
| 完整登录 HTTP 闭环 | NOT VERIFIED |
| Kafka Broker E2E | NOT VERIFIED |
| JMeter 性能数据 | NOT VERIFIED |
| MySQL/Kafka/Redis 故障演练 | NOT VERIFIED |
| 生产运行 | NOT PROVEN |
