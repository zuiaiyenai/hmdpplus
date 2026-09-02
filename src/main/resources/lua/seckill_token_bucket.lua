-- KEYS: activity bucket, IP bucket, user bucket
-- ARGV: windowMillis, activityCapacity, ipCapacity, userCapacity, admissionMultiplier
redis.replicate_commands()
local window = tonumber(ARGV[1])
local multiplier = math.max(0.01, math.min(1, tonumber(ARGV[5]) or 1))
local capacities = {
    math.max(1, math.floor(tonumber(ARGV[2]) * multiplier)),
    math.max(1, math.floor(tonumber(ARGV[3]) * multiplier)),
    math.max(1, math.floor(tonumber(ARGV[4]) * multiplier))
}
local redisTime = redis.call('TIME')
local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
local tokens = {}

for i = 1, 3 do
    local last = tonumber(redis.call('HGET', KEYS[i], 'last')) or now
    local current = tonumber(redis.call('HGET', KEYS[i], 'tokens')) or capacities[i]
    current = math.min(capacities[i], current + math.max(0, now - last) * capacities[i] / window)
    tokens[i] = current
    if current < 1 then
        return i
    end
end

for i = 1, 3 do
    redis.call('HSET', KEYS[i], 'tokens', tokens[i] - 1)
    redis.call('HSET', KEYS[i], 'last', now)
    redis.call('PEXPIRE', KEYS[i], math.max(1000, window * 2))
end
return 0
