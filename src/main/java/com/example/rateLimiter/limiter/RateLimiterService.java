package com.example.rateLimiter.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    private static final int CAPACITY = 10;
    private static final int REFILL_RATE = 5; // tokens per second

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(Long.class);
        this.script.setScriptText(RedisLuaScript.TOKEN_BUCKET);
    }

    public boolean allowRequest(String keyId) {
        String key = "rate_limiter:" + keyId;

        try {
            Long result = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(CAPACITY),
                    String.valueOf(REFILL_RATE),
                    String.valueOf(System.currentTimeMillis())
            );
            return result != null && result == 1;
        } catch (Exception e) {
            // Fail-open strategy
            return true;
        }
    }
}

