-- ARGV: voucherId, userId, orderId, currentTimeMillis, accessToken, accessTokenEnabled
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local currentTime = tonumber(ARGV[4])
local accessToken = ARGV[5]
local accessTokenEnabled = ARGV[6] == '1'

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local metaKey = 'seckill:meta:' .. voucherId
local accessTokenKey = 'seckill:access:token:{' .. voucherId .. '}:' .. userId
local handoffKey = 'seckill:order:handoff:{' .. voucherId .. '}'
local recoveryKey = 'seckill:recovery:' .. voucherId
local acceptedKey = 'seckill:order:accepted'

-- 对账恢复时暂停接收新预扣，避免恢复快照与请求交叉。
if redis.call('EXISTS', recoveryKey) == 1 then
    return 8
end

local beginTime = tonumber(redis.call('HGET', metaKey, 'beginTime'))
local endTime = tonumber(redis.call('HGET', metaKey, 'endTime'))
local status = tonumber(redis.call('HGET', metaKey, 'status'))
if not currentTime or not beginTime or not endTime or not status then
    return 3
end
if currentTime < beginTime then
    return 4
end
if currentTime > endTime then
    return 5
end
if status ~= 1 then
    return 6
end

local stock = redis.call('GET', stockKey)
if not stock or tonumber(stock) <= 0 then
    return 1
end
if redis.call('SISMEMBER', orderKey, userId) == 1 then
    return 2
end

-- 令牌校验、消费与预扣必须在同一个原子边界内。
if accessTokenEnabled then
    local storedToken = redis.call('GET', accessTokenKey)
    if not storedToken or storedToken ~= accessToken then
        return 7
    end
    redis.call('DEL', accessTokenKey)
end

redis.call('INCRBY', stockKey, -1)
redis.call('SADD', orderKey, userId)

-- Handoff 是 Redis 预扣记录；受理凭证供落库前按订单 ID 确认归属。
redis.call('ZADD', handoffKey, currentTime, orderId .. '|' .. userId .. '|0')
redis.call('HSET', acceptedKey, orderId, userId .. '|' .. voucherId)
return 0
