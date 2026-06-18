-- token_bucket.lua
-- Atomic Token Bucket algorithm for EPS rate limiting
-- Executed via ReactiveRedisTemplate.execute()
--
-- KEYS[1] = bucket key  e.g. "bucket:550e8400-e29b-41d4-a716-446655440000"
-- ARGV[1] = capacity     (epsQuota * burstMultiplier)
-- ARGV[2] = refill_rate  (epsQuota tokens/sec)
-- ARGV[3] = now_ms       (current time in milliseconds)
-- ARGV[4] = requested    (number of tokens to consume, usually 1)
--
-- Returns: {allowed (1|0), remaining_tokens (integer)}

local bucket_key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- Read current bucket state from Redis hash
local data = redis.call('HMGET', bucket_key, 'tokens', 'last_refill_ms')

-- Initialize tokens (first request: bucket starts full)
local tokens = capacity
if data[1] ~= false and data[1] ~= nil then
    tokens = tonumber(data[1])
end

-- Initialize or read last refill timestamp
local last_refill_ms = now_ms
if data[2] ~= false and data[2] ~= nil then
    last_refill_ms = tonumber(data[2])
end

-- Refill tokens based on elapsed time since last refill
-- tokens_to_add = elapsed_seconds * refill_rate
local elapsed_ms = now_ms - last_refill_ms
if elapsed_ms > 0 then
    local tokens_to_add = (elapsed_ms / 1000.0) * refill_rate
    tokens = math.min(capacity, tokens + tokens_to_add)
    last_refill_ms = now_ms
end

-- Try to consume requested tokens
local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

-- Persist updated state with TTL (auto-cleanup inactive tenant buckets)
redis.call('HMSET', bucket_key, 'tokens', tokens, 'last_refill_ms', last_refill_ms)
redis.call('EXPIRE', bucket_key, 7200) -- 2h TTL for inactive buckets

return {allowed, math.floor(tokens)}
