-- quota_check.lua
-- Atomic monthly event-volume quota enforcement
-- Executed via ReactiveRedisTemplate.execute()
--
-- KEYS[1] = monthly quota key e.g. "quota:monthly:{tenantId}:{window}"
-- ARGV[1] = monthly_quota     (max events per 30-day window)
-- ARGV[2] = requested         (number of events to consume)
-- ARGV[3] = monthly_ttl_sec   (TTL for monthly key, e.g. 31d)
--
-- Returns: {allowed_count (0 or requested)} —
--    requested if within quota, 0 if exceeded.
--    Fails closed: if quota is exceeded, the counter is not incremented.

local monthly_key = KEYS[1]
local monthly_quota = tonumber(ARGV[1])
local requested = tonumber(ARGV[2])
local monthly_ttl = tonumber(ARGV[3])

-- Read current counter (default 0 if key doesn't exist)
local monthly_count = redis.call('GET', monthly_key)
monthly_count = monthly_count and tonumber(monthly_count) or 0

-- Check if requested amount would exceed quota
if monthly_count + requested > monthly_quota then
    return {0}
end

-- Within limit — atomically increment
redis.call('INCRBY', monthly_key, requested)
redis.call('EXPIRE', monthly_key, monthly_ttl)

return {requested}
