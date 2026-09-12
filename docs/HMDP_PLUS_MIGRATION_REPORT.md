# HMDP Plus 迁移与验收报告

> 目标仓库：`D:\code\heimadianping\hm-dianping`
> 参考仓库：`D:\code\hmdp-plus`（只读）
> 分支：`feature/hmdp-plus-migration`
> 验收日期：2026-09-12
> 证据口径：源码、自动化测试、真实运行、生产证明分开记录。
> 本报告记录迁移阶段验收；后续真实资格复验结果以 [HMDP_FINAL_QUALIFICATION_REPORT.md](HMDP_FINAL_QUALIFICATION_REPORT.md) 为准。

## 1 项目背景

本次工作不是整仓复制 `hmdp-plus`，而是在原版黑马点评的 Java 8/Spring Boot 2.3.12 单体基础上，选择性迁移缓存、并发控制、可靠异步订单、营销与工程化能力。目标是得到一套能运行、能测试、能解释、适合 Java 后端求职的实现，同时保留目标仓库已有的博客、关注、签到等功能。

迁移阶段曾按自定义 100 分口径评为 86%；该数字已被 159 项最终矩阵取代。2026-09-12 资格复验后的证据加权对齐率为 **96.2%**，只用于审计报告，不代表生产就绪率，也不应写入简历。

## 2 原始 heimadianping 架构

原始项目是典型前后端分离教学单体：Controller → Service → MyBatis-Plus Mapper → MySQL；Redis 承担验证码、登录 Token、商户缓存、点赞、关注、Feed、GEO 和签到。秒杀教学版主要依赖 Lua、Redis 和异步消费思路，缓存及消息可靠性边界较弱。

原始优点是业务链短、学习成本低；主要风险是缓存热点重建、Token 生命周期、管理接口权限、上传路径边界，以及异步订单在数据库或消息组件故障时的可恢复性不足。

## 3 hmdp-plus 架构

本地参考项目采用 Java 17、Spring Boot 3.5.4、多模块结构，包含 `hmdp-core-service`、分库分表、Redisson、Redis 工具、ID 生成、MQ 等模块。它提供了 Caffeine + Redis + Bloom Filter、动态令牌、限流、Handoff、Transactional Outbox、Kafka、号段 ID、订阅通知等可借鉴能力。

参考项目的模块化和 ShardingSphere 并未整体迁入：目标项目仍是 Java 8 单模块。这是有意控制改造半径，不影响学习核心并发与一致性链路。

## 4 两个项目差异

| 维度 | 原始/目标基线 | 参考项目 | 最终实现 |
|---|---|---|---|
| 技术基线 | Java 8、Boot 2.3.12、单模块 | Java 17、Boot 3.5.4、多模块 | 保留 Java 8 单模块 |
| 商户缓存 | Redis Cache Aside | L1 + Bloom + L2 + 锁 | Caffeine → Bloom → Redis → Redisson → MySQL |
| 秒杀异步 | 教学型异步链路 | Handoff/Outbox/Kafka 等增强 | ZSet Handoff → Outbox → Kafka → 批量落库 |
| Redis Stream | 原版教学常见方案 | 不是唯一主链 | 未采用 |
| 数据库 | 业务表为主 | 分片与更多配套表 | Flyway 增加 Outbox、号段和索引 |
| 安全 | 路由和上传边界较宽 | 有部分改进 | 收紧管理路由、上传根边界、登录会话 |
| 验收 | 少量教学测试 | 更宽的工程能力 | 167 个自动化测试 + 本地 MySQL/Redis/API 启动验证 |

## 5 改造路线

实际路线按风险而非机械照搬功能列表：

1. 建立 Git 安全基线并确认目标/参考角色。
2. 用 Lua 建立原子秒杀入口及不确定结果语义。
3. 建立 Redis Handoff、MySQL Transactional Outbox、Kafka 批量落库。
4. 增加订单状态、恢复、取消补偿、对账和分层限流。
5. 增加多级缓存、Bloom Filter、缓存失效 Outbox、数据库号段 ID。
6. 合并营销、资料、密码、博客管理能力并保留本地社交功能。
7. 修复权限、上传、异常消息、Token 生命周期和损坏缓存自愈。
8. 完整构建、真实依赖启动、核心接口抽查并生成学习材料。

## 6 实际完成的改造

- 秒杀：原子 Lua、访问令牌、三级令牌桶、Handoff、Outbox、Kafka 批量消费、生命周期查询、取消补偿、恢复与对账。
- 缓存：商户和秒杀券 Caffeine L1、Bloom Filter、Redis L2、Redisson 重建锁、跨实例失效消息。
- 业务：Feed、点赞、关注、共同关注、GEO、签到/补签、订阅、提醒、通知、Top 买家、资料与密码能力。
- 工程：Flyway、数据库号段 ID、JMeter/Nginx/缓存 A/B 验收材料、集中异常处理和访问控制测试。
- 安全：上传路径归一化与根目录约束，管理接口鉴权，验证码冷却和一次性消费，单用户单 Token 索引。

## 7 Redis 优化

Redis 不再只是简单 KV：String 保存验证码/库存，Hash 保存登录用户，Set 保存关注，ZSet 保存点赞顺序、Feed 和 Handoff，GEO 保存商户坐标，BitMap 保存签到，Bloom Filter 预判不存在 ID。关键 Key 集中在 `RedisConstants`，Lua 脚本位于 `src/main/resources/lua` 和 `src/main/resources/seckill.lua`。

优化重点是原子性和失败语义：Lua 同时校验并写入，比较删除脚本防误删，Redis 执行结果未知时返回“结果未确认，请使用订单ID查询最终状态”，不进行可能造成双重库存的盲目回滚。

## 8 缓存优化

`ShopCacheServiceImpl.queryById` 的读取顺序为 Caffeine L1 → Bloom Filter → Redis/空值标记 → Redisson 双重检查锁 → MySQL。不存在商户使用短 TTL 空值；真实商户使用 Redis TTL 和本地短 TTL。损坏 JSON、`{}` 或 ID 不匹配的 Redis 值会被删除并回源重建。

缓存穿透由 Bloom Filter + 空值缓存共同缓解；缓存击穿由单 Key 重建锁和双重检查缓解；雪崩由不同层级和可配置 TTL 分散风险。写路径通过 Outbox/Kafka 通知各实例失效 L1，但真实 Kafka 联调仍未完成。

## 9 分布式锁

项目同时保留教学用 `SimpleRedisLock` 和工程路径的 Redisson。`SimpleRedisLock` 使用唯一持有者标识与 Lua 比较删除，避免线程 A 超时后误删线程 B 的锁；商户缓存、券缓存和部分 Outbox Relay 使用 Redisson 锁。

显式传入 `leaseTime` 的缓存重建锁不会触发无限续期的 Watchdog 语义；因此面试时不能笼统声称“项目所有 Redisson 锁都依赖 Watchdog”。锁释放前检查 `isHeldByCurrentThread()`。

## 10 秒杀系统

`VoucherOrderServiceImpl.seckillVoucher` 生成订单 ID 后只执行一次 Lua。Lua 校验券元数据、时间、库存、一人一单和访问令牌，成功后扣 Redis 库存、记录购买用户、写入 Handoff ZSet 与 accepted marker。HTTP 线程返回受理结果，不同步写业务订单。

数据库消费者按券分组批量 `INSERT IGNORE`，核对被忽略订单，再按实际插入数条件扣减库存。唯一键、事件 ID、订单 ID 和状态查询共同构成幂等防线。

## 11 Lua

Lua 的价值不是“更快”这一句，而是 Redis 在单线程执行脚本期间不会插入其他命令，因此库存、一人一单、令牌消费和 Handoff 写入要么按同一脚本路径完成，要么返回明确状态码。入口脚本为 `src/main/resources/seckill.lua`；清理、补偿、限流和恢复脚本位于 `src/main/resources/lua/`。

测试已覆盖同一用户并发、库存边界和错误令牌不改变库存/令牌。它证明本地 Redis 下的原子业务规则，不等同于生产峰值证明。

## 12 Redis Stream

最终实现 **没有采用 Redis Stream**。代码中不存在 `XADD`、`XGROUP`、`XREADGROUP` 主链；因此 Consumer Group、ACK 和 Pending List 只能作为对比知识，不能写进“已实现能力”。

选择 ZSet Handoff 的原因是当前链路还需要：按券有序扫描、accepted 状态查询、坏事件隔离、MySQL Outbox 持久化、Kafka 下游和独立恢复/补偿。再叠加 Stream 会形成两套队列语义和更复杂的状态收敛。代价是项目自己实现租约、重试和隔离，代码量高于单纯 Stream。

## 13 异步订单

实际链路：`seckillVoucher` → Redis Lua → `SeckillOrderHandoffRelay.relayOneRound` → `SeckillOrderOutboxBatchWriter` → `SeckillOrderOutboxRelay.relayPending` → Kafka → `SeckillOrderKafkaConsumer` → `VoucherOrderPersistenceServiceImpl.createVoucherOrders`。

Handoff 只有在 Outbox 事务提交后才原子删除；Outbox 使用行租约、重试时间和指数退避；消费者事务完成后手动 ACK。永久冲突可递归拆批，单条坏事件隔离，临时数据库异常保留 Handoff。

## 14 Feed 流

`BlogServiceImpl.saveBlog` 采用推模式，把博客 ID 以时间戳作为 score 写入粉丝收件箱 ZSet。`queryBlogOfFollow(max, offset)` 使用 `reverseRangeByScoreWithScores` 做滚动分页。

`max` 是本页允许的最大时间戳；`offset` 是已经消费的、与 `max` 同分元素数量。下一页传回本页最小时间戳和同分累计偏移，避免相同毫秒发布的博客重复或漏读。普通页码分页在持续插入时会发生位置漂移。

## 15 点赞

`BlogServiceImpl.likeBlog` 在数据库点赞计数更新成功后增删 `blog:liked:<id>` ZSet，score 为时间戳。`queryBlogLikes` 读取前 5 个用户并按 Redis 返回顺序重排，既支持是否点赞，也支持最近点赞用户列表。

当前实现仍存在数据库计数与 Redis 集合跨存储一致性的天然窗口；未引入点赞 Outbox，不能宣称强一致。

## 16 关注

`FollowServiceImpl.follow` 在数据库关系写成功后同步维护用户关注 Set；`followCommons` 使用 Redis `SINTER` 求交集，再批量查询用户并保持交集顺序。数据库是真实关系来源，Redis 是集合运算加速层。

## 17 GEO

`ShopGeoIndexInitializer` 从数据库构建 GEO 索引；`ShopServiceImpl.queryShopByType` 在坐标齐全且按距离排序时进入 `queryShopsByDistance`，使用 GEOSEARCH 获取距离并保持结果顺序。没有坐标时退回数据库分页，避免过去把 `x/y` 置空导致 GEO 分支永远不可达的问题。

Redis GEO 底层以经纬度编码到有序集合 score，并通过地理命令完成半径/距离搜索；它适合附近检索，不替代复杂 GIS。

## 18 BitMap

`UserServiceImpl.sign` 用每月一个 Key、日期减一作为 bit offset；`signCount` 通过 BITFIELD 读取当月至今天的位图并从低位连续计数。项目还支持补签及跨月连续签到统计。

BitMap 的优势是每人每月只需少量位，按天判断和连续统计简单。它不适合保存签到地点、设备等复杂属性，这些仍需普通记录表。

## 19 HyperLogLog

HyperLogLog 仅在 `HmDianPingApplicationTests` 中演示 `add/size`，当前没有 Controller、定时采集、业务 Key 设计或报表链路。因此状态是 **学习性测试存在，线上 UV 功能 NOT IMPLEMENTED**。

可以面试解释其约 0.81% 标准误差和固定小内存优势，但不能写成项目已经完成生产 UV 统计。

## 20 数据库变化

Flyway 配置为 `baseline-on-migrate=true`、基线版本 1、`validate-on-migrate=true`。V2 创建订单、商户缓存、秒杀券 L1 失效 Outbox；V3 调整订单查询索引、迁移补偿状态并创建 `tb_id_segment`。

本轮在已有数据上执行前已留存备份 `D:\code\heimadianping\db-backups\heimadianping-before-flyway-repair-20260912-133022.sql`，SHA-256 为 `D3AD155711C7487AF3A2F68FEF1A866815950C1E5E631F06CBAD79A70D5AAA1C`。最终 Flyway 识别 3 个 migration、数据库版本 3。未执行 repair 或清库。

## 21 Maven 变化

目标仍为 Java 8、Spring Boot 2.3.12。新增或明确使用 Caffeine、Spring AOP、Spring Kafka、Flyway、Redisson，并固定适配的 Spring Data Redis/Lettuce/MySQL 驱动版本。可执行 JAR 为 `target/hm-dianping-0.0.1-SNAPSHOT.jar`。

## 22 架构变化

项目仍是单体，但内部形成三条清晰链路：同步业务 CRUD；多级缓存读写与失效传播；秒杀受理、可靠搬运、消息投递和批量持久化。它不是微服务，也没有因为 Kafka 就自动成为分布式生产系统。

## 23 并发安全

- Lua 防止 Redis 库存超卖和重复受理。
- 数据库条件扣减防止库存变负，唯一键/`INSERT IGNORE` 防重复订单。
- Redisson/DCL 限制热点缓存重建。
- Outbox 行租约防多个实例重复领取同一批事件。
- Token Bucket 对活动、IP、用户三层同时判断，全部通过才扣令牌。
- Token 索引和 Lua compare-delete 避免旧请求删除新会话。

这些是设计与自动化测试证据；没有真实 JMeter 结果时不提供 QPS、P95 或 P99 数字。

## 24 数据一致性

秒杀采用最终一致：Redis 先受理，Handoff 保留暂存，MySQL Outbox 持久记录待投递事件，Kafka 传输，数据库事务批量建单扣库存。查询顺序为业务订单 → Outbox → Redis accepted，因而可返回 `PROCESSING` 而不是误报失败。

缓存采用删除/失效而非跨存储分布式事务。更新事务内写失效 Outbox，消息驱动其他实例清除 L1；Redis L2 和本机 L1 均允许短暂窗口，靠 TTL、消息重试和回源收敛。

## 25 性能优化

已实现的优化手段包括 L1 缓存、Bloom Filter、批量订单持久化、数据库号段 ID、本地号段预取、Feed 滚动分页、GEO 索引和数据库复合索引。它们从设计上减少远程访问、数据库压力或分页漂移。

**没有可引用的真实性能提升百分比。** `scripts/benchmark_shop_cache.py` 和 `load-tests/jmeter/` 是可执行验收材料，不是已经产生的基准结果。

## 26 测试结果

| 层级 | 2026-09-12 结果 | 结论 |
|---|---|---|
| 最终 `mvn test` | 175 tests，0 failure/error/skip | PASS |
| Flyway/MySQL | MySQL 5.7 实连，3 migrations，版本 3 | PASS（本地） |
| Redis | 6379 实连 | PASS（本地） |
| 应用启动 | JAR 在 8081 启动成功 | PASS（本地） |
| 店铺缓存 | 预置损坏缓存后 `GET /shop/1` 返回 200 并重建完整缓存 | PASS |
| 访问控制 | 匿名 `POST /shop` 返回 401 | PASS |
| 验证码冷却 | 首次成功，立即重发返回频繁提示 | PASS |
| 完整验证码登录 | 一次取错验证码导致失败 | **NOT VERIFIED** |
| Kafka Broker E2E | 正常、重复、Consumer 重启、Broker 恢复 | PASS（本地） |
| JMeter/故障注入 | 缓存/秒杀基线及 Kafka/MySQL/Sentinel 演练 | PASS/PARTIAL（见最终资格报告） |

## 27 未解决问题

- 毒消息/DLT、Consumer 写事务中途强杀尚未做真实演练。
- Linux Redis 6.2 + AOF Compose、长稳、备份恢复和发布回滚尚未验证。
- 限流已生效，但异常仍返回 HTTP 200，而不是 429。
- 完整验证码登录、Token 续期和退出的真实 HTTP 闭环未完成；自动化测试已覆盖相关方法。
- HyperLogLog 没有业务入口。
- 点赞/关注的数据库与 Redis 仍是弱一致窗口。
- 默认配置含教学环境凭据，应在真实部署中改为密钥管理和强密码。

## 28 与 hmdp-plus 仍然存在的区别

- 目标是 Java 8/Boot 2.3.12 单模块；参考是 Java 17/Boot 3.5.4 多模块。
- 目标未采用 ShardingSphere 和参考项目的完整框架拆分。
- 目标秒杀主链是 ZSet Handoff + Outbox + Kafka，而不是 Redis Stream。
- 目标保留自己的博客、关注、签到/补签、接口和数据模型。
- 目标安全路由更保守，未照搬参考项目的宽泛匿名范围。

## 29 为什么保留这些区别

升级 Java/Boot、拆模块和引入分库分表会把本次任务从“可靠性迁移”扩大成平台迁移，增加依赖兼容、部署和学习负担。现有单体已能展示缓存、Lua、锁、Outbox、Kafka 和最终一致性。保留安全差异是因为参考实现不是安全规范；保留 Handoff 链路是因为它已经拥有明确状态查询和恢复语义，改为 Stream 收益不足以抵消双重迁移风险。

## 30 简历写法

以下表述只使用已完成能力，不引用虚构吞吐：

- 面向秒杀高并发场景，将同步下单重构为 Lua 原子受理、Redis ZSet Handoff、MySQL Transactional Outbox、Kafka 批量落库链路，并以唯一键、手动 ACK、重试和状态查询保障幂等与最终一致性。
- 针对热点商户查询，构建 Caffeine、Bloom Filter、Redis、Redisson 双重检查锁的多级缓存，并补充空值缓存、损坏缓存自愈和跨实例失效 Outbox，降低缓存穿透与重建并发风险。
- 为秒杀入口实现活动/IP/用户三级 Lua 令牌桶、一次性访问令牌和不确定结果语义，避免攻击流量挤占正常容量，并为已受理未落库订单提供恢复、取消补偿和对账链路。
- 将订单 ID 改为数据库号段分配与 JVM 本地预取，保留显式 Redis 回退模式；通过 Flyway 管理 Outbox、号段表和复合索引演进。
- 完成管理接口鉴权、上传路径根边界、验证码冷却/一次性消费、单用户单 Token 和缓存异常自愈；最终本地 `mvn test` 通过 175 个测试。

## 31 面试问题

建议重点准备：缓存穿透/击穿/雪崩、多级缓存一致性、SETNX 锁误删、Redisson Watchdog、Lua 原子性、超卖与一人一单、异步订单、Handoff/Outbox/Kafka、Redis Stream 对比、消息幂等、Feed `max + offset`、GEO、BitMap、HyperLogLog 证据边界。完整题解见 `docs/HMDP_INTERVIEW_GUIDE.md`。

## 32 面试回答

统一回答框架：先说业务问题，再指出真实类/方法，然后解释数据结构和失败路径，最后说明替代方案与验证边界。例如：

> 我没有把 Redis 扣库存直接等同于下单成功。Lua 成功只表示受理，订单先进入 ZSet Handoff；Relay 在 MySQL Outbox 提交成功后才删除 Handoff，随后由 Kafka 消费者事务性批量建单扣库存。重复事件靠 eventId/orderId 唯一键和 `INSERT IGNORE` 幂等。本地真实 Kafka 已覆盖正常、重复、Consumer 重启和 Broker 恢复，但 Linux Compose、长稳与生产发布仍未证明，所以我只描述为“完成本地真实闭环验证”，不称“生产级”。

## 33 项目启动方法

1. 使用 JDK 8 和 Maven，进入 `D:\code\heimadianping\hm-dianping`。
2. 启动 MySQL 5.7 与 Redis；Kafka 仅在开启异步投递时必需。
3. 清除本机可能污染配置的 `SPRING_CONFIG_ADDITIONAL_LOCATION`，并用环境变量提供数据库/Redis凭据。
4. 执行 `mvn clean package`。
5. 执行 `java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar`，默认端口 8081。
6. 需要 Kafka 主链时设置 `HMDP_KAFKA_ENABLED=true` 和 `HMDP_OUTBOX_ENABLED=true`，并提供 `SPRING_KAFKA_BOOTSTRAP_SERVERS`。
7. 用 `docs/stage-10-verification.md` 执行接口、压测、故障演练和一致性核对；没有保存结果前不得标 PASS。

PowerShell 示例（值使用本机实际配置，不把真实密码提交到 Git）：

~~~powershell
$env:SPRING_CONFIG_ADDITIONAL_LOCATION=''
$env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/heimadianping?useSSL=false&serverTimezone=UTC'
$env:SPRING_DATASOURCE_USERNAME='<db-user>'
$env:SPRING_DATASOURCE_PASSWORD='<db-password>'
$env:SPRING_REDIS_HOST='127.0.0.1'
$env:SPRING_REDIS_PORT='6379'
$env:SPRING_REDIS_PASSWORD='<redis-password>'
mvn clean package
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
~~~
