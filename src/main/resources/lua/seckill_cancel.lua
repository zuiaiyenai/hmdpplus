-- KEYS: stock key, purchased-user set
-- ARGV: userId
if redis.call('SREM', KEYS[2], ARGV[1]) == 0 then
    return 0
end
if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('SADD', KEYS[2], ARGV[1])
    return -1
end
redis.call('INCRBY', KEYS[1], 1)
return 1
