# HMDP Java 后端面试指南

> 使用方式：先在 IDE 打开“项目代码”，再按“原理 → 设计原因 → 替代方案 → 优缺点 → 可直接回答”复述。所有结论以当前仓库为准；Kafka Broker E2E、Sentinel 切换、MySQL 隔离断链和 JMeter 本机基线已验证，生产资格仍是 `NO-GO`。

## 1 为什么 Redis 能解决缓存穿透？

- **项目代码**：`ShopCacheServiceImpl.queryById`、`ShopBloomFilter`、`ShopCacheServiceImpl.cacheNull`。
- **原理**：Bloom Filter 用概率结构快速判定“一定不存在”；对漏过 Bloom 的真实不存在 ID，Redis 保存短 TTL 空值，避免每次打到 MySQL。
- **设计原因**：Bloom 有误判，空值缓存补第二层；数据库仍是最终来源。
- **替代方案**：参数白名单、只缓存空值、布谷鸟过滤器。
- **优缺点**：查询快、减轻数据库；Bloom 有假阳性且需要初始化/维护，空值占空间并有短暂不一致。
- **可直接回答**：项目不是只靠 Redis。先用 `ShopBloomFilter` 拦截确定不存在的 ID，再用短 TTL 空值吸收假阳性，真实数据才进入 Redis/数据库链路。

## 2 缓存击穿是什么？

- **项目代码**：`ShopCacheServiceImpl.queryById`、`SeckillVoucherCacheService.get`。
- **原理**：热点 Key 失效瞬间，大量并发同时回源，数据库被同一 Key 的请求压垮。
- **设计原因**：项目用 Redisson 单 Key 锁和拿锁后的 L1/L2 双重检查，只让一个线程重建。
- **替代方案**：逻辑过期、热点永不过期加主动刷新、请求合并。
- **优缺点**：互斥锁数据新，但等待会放大尾延迟；逻辑过期响应快，但会短暂返回旧值。
- **可直接回答**：我在商户缓存中用“L1 → Redis → Redisson 锁 → 再查 L1/Redis → MySQL”的双重检查解决热点重建竞争。

## 3 缓存雪崩是什么？

- **项目代码**：`ShopLocalCache`、`ShopCacheServiceImpl`、`application.yaml` 的缓存 TTL 配置。
- **原理**：大量 Key 同时失效或 Redis 整体不可用，引发大规模回源。
- **设计原因**：L1/L2 不同 TTL、空值短 TTL、重建锁和依赖失败时明确失败共同缩小影响。
- **替代方案**：随机 TTL、预热、限流降级、Redis 集群/哨兵。
- **优缺点**：分层提高韧性；多层缓存带来失效传播和观测复杂度。
- **可直接回答**：击穿针对一个热点 Key，雪崩针对大量 Key 或整个缓存层。项目用分层 TTL、L1、重建锁和失效消息降低风险，但没有做 Sentinel 故障切换实测。

## 4 逻辑过期和互斥锁有什么区别？

- **项目代码**：`CacheClient.queryWithLogicalExpire`、`ShopCacheServiceImpl.queryById`。
- **原理**：逻辑过期保留旧数据并异步重建；互斥锁让未命中请求等待或回退，只由持锁者同步回源。
- **设计原因**：商户详情选择互斥重建以优先数据新鲜度；券缓存也提供逻辑 TTL 能力。
- **替代方案**：主动刷新、refresh-ahead、单飞请求。
- **优缺点**：逻辑过期可用性高但返回旧值；互斥锁一致性直观但尾延迟更高。
- **可直接回答**：两者是在“新鲜度”和“可用性”之间取舍，不是谁绝对更高级。

## 5 为什么分布式锁不能直接使用 SETNX？

- **项目代码**：`SimpleRedisLock.tryLock/unlock`、`src/main/resources/unlock.lua`。
- **原理**：只有 SETNX 没有 TTL 会死锁；分两条命令设置锁和过期会有原子性窗口；固定值释放会误删别人的锁。
- **设计原因**：项目用带 TTL 的 `setIfAbsent` 原子加锁，并把 UUID/线程标识作为 value。
- **替代方案**：Redisson、数据库悲观锁、ZooKeeper/etcd 租约。
- **优缺点**：Redis 锁轻量；仍要处理过期、续期、主从切换和业务幂等。
- **可直接回答**：SETNX 只是抢锁动作，不是完整锁协议，必须一起解决租约、所有权校验和释放原子性。

## 6 如何避免误删其他线程的锁？

- **项目代码**：`SimpleRedisLock.unlock`、`unlock.lua`、`ShopCacheServiceImpl` 的 `isHeldByCurrentThread`。
- **原理**：释放时先比较 value 是否是自己的，再 DEL；比较和删除必须在同一个 Lua 脚本中。
- **设计原因**：线程 A 超时后，线程 B 可能已获得同名锁，A 不能直接删除 B 的锁。
- **替代方案**：Redisson RLock 封装所有权。
- **优缺点**：Lua 方案清楚、依赖少；续期和可重入仍需自行实现。
- **可直接回答**：锁 value 放唯一 owner，释放时 Lua 做 `GET == owner` 后再 `DEL`，不使用 Java 端先 GET 再 DEL。

## 7 为什么需要 Lua？Lua 为什么具有原子性？

- **项目代码**：`VoucherOrderServiceImpl.seckillVoucher`、`src/main/resources/seckill.lua`。
- **原理**：Redis 在执行脚本时把脚本作为一个不可被其他命令插入的执行单元，因此多步读写不会交错。
- **设计原因**：库存、重复购买、访问令牌和 Handoff 必须基于同一时刻状态决策。
- **替代方案**：Redis 事务 WATCH/MULTI、数据库事务、服务端存储过程。
- **优缺点**：少网络往返且原子；脚本过长会阻塞 Redis，逻辑和可观测性更难。
- **可直接回答**：Lua 解决的是跨多个 Redis 命令的竞态，不代表 Redis 与 MySQL 也进入同一事务，所以后面仍需要 Handoff 和 Outbox。

## 8 Redisson Watchdog 是什么？

- **项目代码**：`RedissonConfig`、`ShopCacheServiceImpl.queryById`。
- **原理**：Redisson 在未显式指定 leaseTime 的加锁方式中，可由 Watchdog 周期续租，避免业务未完成锁先过期。
- **设计原因**：适合耗时不确定的临界区。
- **替代方案**：显式短租约、自己续期、数据库租约。
- **优缺点**：减少锁提前过期；进程长暂停、网络和主从一致性仍需考虑。
- **可直接回答**：项目使用 Redisson，但商户缓存 `tryLock(waitTime, leaseTime, ...)` 显式给了租期，不能把该路径说成靠 Watchdog 自动续期。

## 9 为什么秒杀会超卖？

- **项目代码**：`seckill.lua`、`VoucherOrderPersistenceServiceImpl.createVoucherOrders`、`VoucherOrderMapper.decrementStock`。
- **原理**：并发请求如果都先读到库存大于零，再分别扣减，会发生检查与更新竞态。
- **设计原因**：Redis Lua 原子预扣，数据库再按实际插入数条件扣减，形成双层防线。
- **替代方案**：数据库 `stock = stock - 1 where stock > 0`、悲观锁、乐观锁版本号。
- **优缺点**：Redis 吞吐高；需要解决 Redis 与 MySQL 最终一致性。
- **可直接回答**：超卖根因不是“请求多”，而是库存检查和扣减不原子。项目入口和落库各有条件扣减。

## 10 如何实现一人一单？

- **项目代码**：`seckill.lua` 的已购集合、`VoucherOrderPersistenceServiceImpl`、`V3__complete_stage_7_to_9_infrastructure.sql` 的订单查询索引。
- **原理**：Redis 原子检查并写入用户集合，数据库插入时再校验订单冲突/唯一身份。
- **设计原因**：只靠 Redis 遇到重放或数据修复时不够，只靠数据库会把热点竞争下沉到 MySQL。
- **替代方案**：数据库唯一索引 `(user_id, voucher_id)`；若取消后允许再买，则唯一约束需包含有效状态或使用独立占位表。
- **优缺点**：双层防护稳健；取消语义使数据库约束设计更复杂。
- **可直接回答**：Redis 负责入口快速拒绝，数据库负责最终事实，重复消息不能再次扣库存。

## 11 为什么不能只依赖数据库锁？

- **项目代码**：`VoucherOrderServiceImpl.seckillVoucher`、`SeckillRateLimitServiceImpl`、`VoucherOrderPersistenceServiceImpl`。
- **原理**：数据库锁能保证正确性，但所有突发请求都进入连接池和事务，会先耗尽数据库容量。
- **设计原因**：在 Redis 入口完成限流、资格和库存判断，把数据库留给已受理事件的批量落库。
- **替代方案**：纯数据库条件更新、队列表削峰。
- **优缺点**：Redis 前置提高容量隔离；组件更多且要处理最终一致性。
- **可直接回答**：数据库仍是最后防线，但不让它承担秒杀入口流量。

## 12 为什么把订单创建改成异步？

- **项目代码**：`VoucherOrderServiceImpl.seckillVoucher`、`SeckillOrderHandoffRelay`、`SeckillOrderOutboxRelay`。
- **原理**：HTTP 线程只完成资格受理，后端按可控批次写数据库，削平峰值并缩短入口临界区。
- **设计原因**：同步返回订单落库会把响应时延绑定到 MySQL/Kafka 状态。
- **替代方案**：同步事务、内存队列、Redis Stream、RabbitMQ。
- **优缺点**：吞吐与韧性更好；用户看到的是受理/处理中，必须提供状态查询和补偿。
- **可直接回答**：异步不是“丢给线程池不管”，项目用 Handoff 和 Outbox 保存可恢复状态。

## 13 Redis Stream 和 RabbitMQ/Kafka 有什么区别？

- **项目代码**：当前无 Stream 主链；对比 `SeckillOrderKafkaConfig` 和 `SeckillOrderOutboxRelay`。
- **原理**：Stream 是 Redis 内的数据流，提供消费组和 Pending；RabbitMQ 擅长路由/确认，Kafka 擅长分区日志、回放和大吞吐。
- **设计原因**：项目已有 Kafka 作为跨实例消息层，Redis 只做 Handoff 暂存，不再叠加 Stream。
- **替代方案**：直接用 Stream 完成小规模异步队列。
- **优缺点**：Stream 部署简单但与 Redis 资源耦合；专业 MQ 的治理、保留和生态更强，部署更重。
- **可直接回答**：我能解释 Stream，但项目最终没有采用它，不能把 Stream Consumer Group 写成已实现。

## 14 Redis Stream 为什么需要 Consumer Group？

- **项目代码**：`NOT IMPLEMENTED`，仅作方案对比。
- **原理**：消费组把消息在多个消费者间分配，并记录已投递未确认消息，支持扩展和恢复。
- **设计原因**：若用 Stream 承担订单队列，必须避免每个消费者都收到同一条消息，并追踪处理进度。
- **替代方案**：独立消费者全量读、Redis List、专业 MQ consumer group。
- **优缺点**：组内负载均衡方便；需要维护组、消费者和 Pending。
- **可直接回答**：这是候选方案知识，不是当前代码证据；当前对应能力由 Kafka consumer group、手动 ACK 和 Outbox 状态完成。

## 15 Pending List 是什么？消费者宕机怎么办？

- **项目代码**：Stream PEL `NOT IMPLEMENTED`；当前看 `tb_seckill_order_outbox`、`SeckillOrderOutboxRelay.relayPending` 和 Kafka 手动 ACK。
- **原理**：Stream PEL 保存已投递未 ACK 消息，其他消费者可 claim；当前项目用 MySQL Outbox 的 `status/relay_owner/relay_lease_until/next_retry_time` 保存类似可恢复状态。
- **设计原因**：消费者宕机不能把“已投递”当“已完成”。
- **替代方案**：Kafka offset + retry/DLT、RabbitMQ requeue、数据库任务表。
- **优缺点**：持久状态可恢复，但要防永久毒消息无限重试。
- **可直接回答**：当前不是 Stream PEL，而是 Outbox 行租约过期后重新领取，Kafka 事务落库成功后才 ACK，坏消息走隔离/DLT 设计；真实 Broker 已验证正常/重复投递和 Consumer 重启，毒消息与消费事务中途强杀仍是发布前演练项。

## 16 如何保证订单幂等？

- **项目代码**：`SeckillOrderOutboxEvent`、`V2__create_outbox_infrastructure.sql`、`VoucherOrderPersistenceServiceImpl.createVoucherOrders`。
- **原理**：同一业务事件始终携带相同 eventId/orderId；Outbox 对两者建唯一键，消费者 `batchInsertIgnore` 后核对已存在记录，只按实际新增数量扣库存。
- **设计原因**：消息系统通常是至少一次投递，重试和宕机恢复必然可能重复。
- **替代方案**：消费记录表、幂等 Key、业务状态机 CAS。
- **优缺点**：数据库唯一约束是最终保险；仍需正确区分“相同重试”和“ID 冲突不同业务”。
- **可直接回答**：幂等不是简单 catch DuplicateKey，而是稳定业务 ID、唯一约束、忽略后核对和库存按新增数扣减的组合。

## 17 Redis 和 MySQL 数据一致性怎么保证？

- **项目代码**：`SeckillOrderHandoffService`、`SeckillOrderOutboxBatchWriter`、`SeckillOrderLifecycleService`、`SeckillOrderReconciliationService`。
- **原理**：不做跨 Redis/MySQL 的 2PC，而用可恢复中间状态和幂等消费实现最终一致。
- **设计原因**：Lua 成功后 Redis 已受理；Outbox 提交成功才删除 Handoff；状态查询依次看订单、Outbox、accepted marker。
- **替代方案**：TCC/Saga、可靠消息最终一致、定时对账。
- **优缺点**：可用性和性能较好；有短暂不一致窗口，状态机与补偿更复杂。
- **可直接回答**：项目保证的是可恢复的最终一致，不是瞬时强一致；故障演练脚本已有但还没真实验收。

## 18 Feed 流为什么使用 ZSet？

- **项目代码**：`BlogServiceImpl.saveBlog`、`queryBlogOfFollow`。
- **原理**：member 保存博客 ID，score 保存发布时间，天然支持按时间倒序和分数范围滚动查询。
- **设计原因**：关注 Feed 需要稳定时间线，不只是无序集合。
- **替代方案**：数据库游标、Kafka 建索引、专用搜索系统。
- **优缺点**：查询简单快速；推模式对大 V 粉丝写放大明显。
- **可直接回答**：当前适合普通用户推模式，大 V 场景可演进为推拉结合。

## 19 为什么不能使用普通分页？

- **项目代码**：`BlogServiceImpl.queryBlogOfFollow(Long max, Integer offset)`。
- **原理**：第一页后如果插入新内容，`page/size` 的位置整体后移，第二页可能重复或漏数据。
- **设计原因**：以 score 游标固定时间边界，让新增内容不会改变旧分页窗口。
- **替代方案**：数据库基于 `(created_time,id)` 的 Keyset Pagination。
- **优缺点**：游标稳定；不能随意跳到第 N 页。
- **可直接回答**：时间线优先连续浏览，不追求页码跳转，所以用滚动分页。

## 20 Scroll Pagination 为什么需要 offset？

- **项目代码**：`BlogServiceImpl.queryBlogOfFollow`、`ScrollResult`。
- **原理**：多个博客可能具有相同毫秒 score。`max` 只能定位到分数，`offset` 表示这个相同分数已消费多少条。
- **设计原因**：下一页从相同最小 score 继续时跳过已读同分项，防重复/漏读。
- **替代方案**：把时间戳和唯一 ID 组合成严格单调游标。
- **优缺点**：兼容现有 ZSet score；客户端必须原样传回 `minTime/offset`。
- **可直接回答**：`offset` 不是普通页偏移，而是“当前最小时间戳下已读元素数”。

## 21 GEO 底层是什么？

- **项目代码**：`ShopGeoIndexInitializer`、`ShopServiceImpl.queryShopsByDistance`。
- **原理**：Redis 将经纬度编码并存入 ZSet，通过 GEO 命令做邻近范围和距离计算。
- **设计原因**：店铺附近搜索只需轻量半径检索和分页。
- **替代方案**：MySQL 空间索引、PostGIS、Elasticsearch geo_distance。
- **优缺点**：Redis 查询快、接入简单；复杂多边形和高精 GIS 不适合。
- **可直接回答**：坐标完整时走 GEOSEARCH，没有坐标就回退数据库；该分支已有自动化测试和本地代码验证。

## 22 BitMap 为什么适合签到？

- **项目代码**：`UserServiceImpl.sign/signCount/makeUpSign/countConsecutiveSignDays`。
- **原理**：一个 bit 表示一天是否签到，31 天只需 31 bit；BITFIELD 取出整数后用位运算统计末尾连续 1。
- **设计原因**：签到是高密度布尔状态，不需要每条都存完整对象。
- **替代方案**：签到明细表、Redis Set。
- **优缺点**：省内存、统计快；不保存时间、地点等属性。
- **可直接回答**：项目按“用户 + 年月”分 Key，offset 是日期减一，并支持跨月连续统计。

## 23 HyperLogLog 为什么适合 UV？

- **项目代码**：`HmDianPingApplicationTests` 中的 `opsForHyperLogLog().add/size`。
- **原理**：基于概率基数估计，用固定小内存估算大量去重元素，标准误差约 0.81%。
- **设计原因**：UV 通常允许小误差，但精确 Set 的内存随用户数增长。
- **替代方案**：Set 精确去重、离线数仓 distinct、Bitmap（需要紧凑整数 ID）。
- **优缺点**：内存稳定；无法列出用户，也不能精确计数。
- **可直接回答**：当前仓库只有测试演示，没有真实 UV 接口和采集链，所以简历不能写“完成线上 UV”。

## 24 如何解释这套项目的验证边界？

- **项目代码/材料**：`target/surefire-reports`、`docs/stage-10-verification.md`、`load-tests/jmeter/`。
- **原理**：单元测试证明分支逻辑，集成测试证明局部依赖交互，真实 API 证明当前进程与依赖可工作，生产证明还需要真实容量、故障和长期运行证据。
- **设计原因**：避免把 mock、配置文件或脚本存在夸大成生产能力。
- **替代方案**：CI 环境、Testcontainers、预发布压测、混沌演练。
- **优缺点**：证据分级更诚实；结论看起来不如虚构数字“亮眼”，但面试更经得起追问。
- **可直接回答**：当前最终回归是 175 个测试全过；本地真实 Kafka E2E、Broker/Consumer 恢复、Sentinel 切换、MySQL 隔离断链和 JMeter 基线已验证。但远程 CI 尚未绿、Linux Redis 6.2 + AOF Compose 和长稳/备份恢复未验证，所以我仍把它定位为证据较完整的求职项目，而不是生产级系统。

## 面试前 5 分钟代码路线

1. `VoucherOrderServiceImpl.seckillVoucher`：入口为什么只有一次 Lua。
2. `src/main/resources/seckill.lua`：资格、库存、一人一单、令牌、Handoff 如何同原子单元完成。
3. `SeckillOrderHandoffRelay.relayOneRound`：为什么 Outbox 提交后才清 Handoff。
4. `SeckillOrderOutboxRelay.relayPending` 与 `VoucherOrderPersistenceServiceImpl.createVoucherOrders`：租约、重试、幂等、批量事务。
5. `ShopCacheServiceImpl.queryById`：多级缓存与热点重建。
6. `BlogServiceImpl.queryBlogOfFollow`：`max + offset`。
7. `UserServiceImpl.signCount`：BitMap 位运算。
