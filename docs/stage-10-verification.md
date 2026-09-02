# 阶段 10：测试、故障演练与压测验收

本目录提供可复现的验收流程。只有实际执行并保存结果的项目才能标记为“通过”；脚本存在不等于完成压测或故障演练。

## 1. 启动本地依赖

~~~powershell
docker compose up -d mysql redis kafka
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3308/heimadianping?useSSL=false&serverTimezone=UTC'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '123456'
$env:SPRING_REDIS_HOST = '127.0.0.1'
$env:SPRING_REDIS_PORT = '6380'
$env:SPRING_REDIS_PASSWORD = '123456'
$env:HMDP_KAFKA_ENABLED = 'true'
$env:HMDP_OUTBOX_ENABLED = 'true'
mvn spring-boot:run
~~~

应用默认端口为 8081。application-redis-sentinel.yaml 是可选 Sentinel Profile；单机 Compose 验收不要启用该 Profile。

## 2. 自动化测试

~~~powershell
mvn test
git diff --check
~~~

测试至少应记录：用例总数、失败数、跳过数、MySQL/Redis/Kafka 是否实际可用、Flyway 当前版本。外部服务未启动和代码断言失败必须分开记录。

## 3. 商户多级缓存 A/B

先预热，再用完全相同的请求数和并发分别测试 L1 开启/关闭。每次切换配置都重启应用，并保持 MySQL、Redis、数据集和机器负载一致。

~~~powershell
python scripts/benchmark_shop_cache.py --url http://127.0.0.1:8081/shop/1 --scenario l1-on --warmup 1000 --requests 10000 --concurrency 40
~~~

关闭 L1 时设置 $env:HMDP_SHOP_LOCAL_CACHE_ENABLED='false'，重启后以 --scenario l1-off 重跑。保存 JSON 输出中的吞吐、p50、p95、p99 和错误数。

## 4. 秒杀容量与限流压测

JMeter 场景、用户准备 Lua、清理 Lua 和独立 Nginx 配置位于 load-tests/jmeter/。先阅读其中的 README.md，使用专用测试券和测试用户，禁止直接污染日常数据。

~~~powershell
jmeter -n -t load-tests/jmeter/seckill-capacity.jmx -Jhost=127.0.0.1 -Jport=8081 -Jthreads=200 -Jduration_seconds=30 -l target/seckill-capacity.jtl
~~~

容量验收同时采集：入口吞吐与 p95/p99、HTTP/业务错误、Outbox backlog 与最老事件年龄、Kafka lag、MySQL 订单落库速度、连接池 active/pending、Redis 延迟和 JVM CPU/GC。

## 5. 故障演练

每项演练都先记录基线，再注入故障，最后恢复并等待积压归零。

### Kafka 中断

~~~powershell
docker compose stop kafka
# 运行一小段专用券流量
docker compose start kafka
~~~

预期：已受理订单保留在 Redis Handoff/MySQL Outbox；Kafka 恢复后继续使用相同 eventId/orderId 投递，最终 PENDING/SENT 积压归零。Kafka 停止期间不得把“已受理”误报为最终失败。

### MySQL 中断

先正常请求一次以取得本 JVM 的数据库号段，再停止 MySQL：

~~~powershell
docker compose stop mysql
# 验证当前号段仍可发号，并运行短时专用券流量
docker compose start mysql
~~~

预期：当前号段用尽前不依赖数据库分配新 ID；20% 阈值触发的异步预取失败不应破坏当前号段。MySQL 恢复后重新分配号段，Handoff/Outbox 继续处理。若启动时没有可用号段，数据库模式发号失败属于预期；可用 $env:HMDP_SECKILL_ORDER_ID_MODE='redis' 验证显式 Redis 回退模式。

### Redis 中断

~~~powershell
docker compose stop redis
# 验证缓存和秒杀入口返回明确失败，不得返回虚假成功
docker compose start redis
~~~

恢复后核对秒杀元数据、库存、Bloom Filter、GEO 索引和本地缓存是否由初始化/失效链路恢复。

## 6. 一致性核对

将 <voucherId> 替换为专用测试券 ID。

~~~sql
SELECT status, COUNT(*) AS orders, COUNT(DISTINCT user_id) AS users
FROM tb_voucher_order
WHERE voucher_id = <voucherId>
GROUP BY status;

SELECT status, COUNT(*) AS events, MIN(created_time) AS oldest
FROM tb_seckill_order_outbox
GROUP BY status;

SELECT status, COUNT(*) AS events, MIN(created_time) AS oldest
FROM tb_shop_cache_invalidation_outbox
GROUP BY status;

SELECT status, COUNT(*) AS events, MIN(created_time) AS oldest
FROM tb_seckill_voucher_l1_invalidation_outbox
GROUP BY status;

SELECT biz_tag, max_id, step, version
FROM tb_id_segment
WHERE biz_tag = 'voucher-order';
~~~

~~~powershell
redis-cli -h 127.0.0.1 -p 6380 -a 123456 GET "seckill:stock:<voucherId>"
redis-cli -h 127.0.0.1 -p 6380 -a 123456 SCARD "seckill:order:<voucherId>"
redis-cli -h 127.0.0.1 -p 6380 -a 123456 ZCARD "seckill:order:handoff:{<voucherId>}"
redis-cli -h 127.0.0.1 -p 6380 -a 123456 ZCARD "seckill:order:accepted"
~~~

最终判定必须结合初始库存、有效订单状态和取消记录；不能只比较一个计数。正常收敛后应满足：无重复有效订单、Redis 已购用户与有效订单用户一致、数据库库存与有效订单变化一致、Handoff 和可自动重试 Outbox 无长期积压。