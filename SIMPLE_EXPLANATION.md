# Distributed Rate Limiter - Key Functions & Flow

## 🎯 What It Does
A **distributed rate limiter** using Spring Boot + Redis + Token Bucket algorithm. Limits requests per user across multiple app instances.

## 🔄 Request Flow

```
Client Request → RateLimitFilter → RateLimiterService → Redis Lua Script → Response
```

## 📂 Key Functions & Classes

### 1. **RateLimitFilter.java** (Entry Point)
```java
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
    String apiKey = request.getHeader("X-API-KEY");
    
    if (apiKey == null) {
        response.setStatus(401);  // No API key → 401
        return;
    }
    
    if (!limiter.allowRequest(apiKey)) {
        response.setStatus(429);  // Rate limit exceeded → 429
        return;
    }
    
    chain.doFilter(request, response);  // Allow request
}
```
**Purpose**: Intercepts every request, checks API key & rate limit before controller.

---

### 2. **RateLimiterService.java** (Core Logic)
```java
public boolean allowRequest(String keyId) {
    String key = "rate_limiter:" + keyId;
    
    Long result = redisTemplate.execute(
        script,
        List.of(key),                          // Redis key
        String.valueOf(CAPACITY),              // 10 tokens max
        String.valueOf(REFILL_RATE),           // 5 tokens/sec refill
        String.valueOf(System.currentTimeMillis())
    );
    
    return result == 1;  // 1 = allowed, 0 = blocked
}
```
**Purpose**: Calls Redis Lua script with user key, capacity, refill rate, and current time.

---

### 3. **RedisLuaScript.java** (Token Bucket Algorithm)
```lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])     -- 10
local refill_rate = tonumber(ARGV[2])  -- 5 tokens/sec
local now = tonumber(ARGV[3])

local data = redis.call("HMGET", key, "tokens", "last_refill_time")
local tokens = tonumber(data[1])
local last_refill = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_refill = now
end

local delta = now - last_refill
local refill = delta * refill_rate / 1000
tokens = math.min(capacity, tokens + refill)

local allowed = 0
if tokens >= 1 then
    allowed = 1
    tokens = tokens - 1
end

redis.call("HMSET", key, "tokens", tokens, "last_refill_time", now)
redis.call("EXPIRE", key, 3600)
return allowed
```
**Purpose**: Atomic token bucket implementation on Redis.

---

### 4. **DemoController.java** (Test Endpoint)
```java
@GetMapping("/data")
public String data() {
    return "Success";
}
```
**Purpose**: Simple endpoint to test rate limiting.

---

## ⚙️ Configuration
```java
private static final int CAPACITY = 10;    // Max tokens per user
private static final int REFILL_RATE = 5;  // Tokens added per second
```

## 🧪 Testing
```bash
# Missing API key
curl http://localhost:8080/api/data
# → 401 Unauthorized

# Valid request
curl -H "X-API-KEY: user1" http://localhost:8080/api/data  
# → 200 Success

# Rate limit exceeded (after 10 requests)
curl -H "X-API-KEY: user1" http://localhost:8080/api/data
# → 429 Too Many Requests
```

## 🎯 Key Points to Explain

1. **Filter intercepts requests** before they reach controller
2. **Service calls Redis Lua script** with user key and parameters  
3. **Lua script runs atomically** on Redis (no race conditions)
4. **Token bucket**: 10 tokens max, refill 5/sec, each request costs 1 token
5. **Distributed**: Works across multiple app instances via Redis
6. **Fail-open**: If Redis fails, allows requests anyway

## 📋 Simple Explanation

"This project limits API requests using a token bucket. Each user gets 10 tokens that refill at 5 per second. The filter checks every request - if no API key, return 401. If no tokens left, return 429. Otherwise, consume 1 token and allow the request. The magic happens in a Redis Lua script that ensures atomic operations across distributed servers."</content>
<parameter name="filePath">/Users/abinfinity/Desktop/projects/rateLimiter/SIMPLE_EXPLANATION.md
