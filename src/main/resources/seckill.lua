-- KEYS[1] 秒杀库存 key，KEYS[2] 已下单用户集合 key，ARGV[1] 用户 id
local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock or stock <= 0 then
    return 1
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 2
end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 0
