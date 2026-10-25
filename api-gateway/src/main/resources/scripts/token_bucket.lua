-- Token-bucket rate limiter
-- KEYS[1] = tokens key, KEYS[2] = timestamp key
-- ARGV[1] = refill rate (tokens/sec), ARGV[2] = capacity
-- ARGV[3] = now (ms), ARGV[4] = requested tokens
-- Returns { allowed (1/0), remaining_tokens, retry_after_ms }

local tokens_key = KEYS[1]
local ts_key = KEYS[2]

local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local ttl = math.max(2, math.floor((capacity / rate) * 2))

local last_tokens = tonumber(redis.call("get", tokens_key))
if last_tokens == nil then
  last_tokens = capacity
end

local last_ts = tonumber(redis.call("get", ts_key))
if last_ts == nil then
  last_ts = now
end

local delta_ms = math.max(0, now - last_ts)
local refilled = last_tokens + (delta_ms * rate / 1000.0)
if refilled > capacity then refilled = capacity end

local allowed = 0
local retry_after = 0
local new_tokens = refilled

if refilled >= requested then
  allowed = 1
  new_tokens = refilled - requested
else
  local missing = requested - refilled
  retry_after = math.ceil((missing / rate) * 1000)
end

redis.call("setex", tokens_key, ttl, tostring(new_tokens))
redis.call("setex", ts_key, ttl, tostring(now))

return { allowed, math.floor(new_tokens), retry_after }
