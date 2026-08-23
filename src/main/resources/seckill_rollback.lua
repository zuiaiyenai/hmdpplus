-- 数据库事务失败时，原子撤销本次 Redis 预扣
if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
    redis.call('INCR', KEYS[1])
    return 1
end
return 0
