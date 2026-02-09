package com.example.rateLimiter.limiter;

public class RedisLuaScript {

    public static final String TOKEN_BUCKET = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        local data = redis.call("HMGET", key, "tokens", "last_refill_time")
        local tokens = tonumber(data[1])
        local last_refill = tonumber(data[2])

        if tokens == nil then
            tokens = capacity
            last_refill = now
        end

        local delta = math.max(0, now - last_refill)
        local refill = delta * refill_rate / 1000
        tokens = math.min(capacity, tokens + refill)

        local allowed = 0
        if tokens >= 1 then
            allowed = 1
            tokens = tokens - 1
        end

        redis.call("HMSET", key,
            "tokens", tokens,
            "last_refill_time", now)

        redis.call("EXPIRE", key, 3600)
        return allowed
        """;
}
