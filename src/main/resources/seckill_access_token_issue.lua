local existing = redis.call('GET', KEYS[1])
if existing then
    return existing
end

redis.call('PSETEX', KEYS[1], tonumber(ARGV[2]), ARGV[1])
return ARGV[1]
