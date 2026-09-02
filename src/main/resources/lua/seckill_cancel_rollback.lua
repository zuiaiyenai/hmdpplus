-- Reverts seckill_cancel.lua; safe when the cancellation script never ran.
if redis.call('SADD', KEYS[2], ARGV[1]) == 0 then
    return 0
end
if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('SREM', KEYS[2], ARGV[1])
    return -1
end
redis.call('DECRBY', KEYS[1], 1)
return 1
