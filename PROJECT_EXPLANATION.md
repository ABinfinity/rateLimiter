# Distributed Rate Limiter - Project Explanation

## 📌 High-Level Overview

This is a **distributed rate limiter** built with **Spring Boot** and **Redis** using the **Token Bucket algorithm**. It protects APIs by limiting the number of requests a user can make within a given time period. The system is designed to work across **multiple application instances** (horizontally scaled environments) using a **centralized Redis store**.

---

## 🎯 Core Concepts

### Token Bucket Algorithm
- Each API user gets a **bucket** containing tokens
- **Capacity**: Maximum tokens allowed (e.g., 10 tokens)
- **Refill Rate**: New tokens added per second (e.g., 5 tokens/sec)
- **Each request consumes 1 token**
- If tokens run out → Request rejected with **HTTP 429 (Too Many Requests)**

### Example:
```
Initial: 10 tokens
User makes 10 requests → 0 tokens left → Next request BLOCKED
Wait 2 seconds → Bucket refills with 10 tokens (5 tokens/sec × 2 sec)
Now can make 10 more requests
```

---

## 🔄 Request Flow (How It Works)

```
1. Client sends HTTP request with X-API-KEY header
         ↓
2. RateLimitFilter intercepts the request
         ↓
3. Check if X-API-KEY exists? 
   - NO  → Return 401 Unauthorized
   - YES → Continue
         ↓
4. RateLimiterService.allowRequest(apiKey)
         ↓
5. Execute Redis Lua Script (atomic operation)
   - Fetch current tokens & last refill time from Redis
   - Calculate new tokens based on elapsed time
   - If tokens >= 1:
     * Decrement token count
     * Allow request (return 1)
   - Else:
     * Request blocked (return 0)
         ↓
6. Check result:
   - Result == 1 → Allow request to proceed to controller
   - Result != 1 → Return 429 Too Many Requests
         ↓
7. DemoController processes the request
   - Returns "Success" with HTTP 200
```

---

## 📂 File Structure & Responsibilities

### 1. **RateLimiterApplication.java** (Main Entry Point)
```java
@SpringBootApplication
public class RateLimiterApplication {
    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
```
- Boots up the Spring Boot application
- Initializes all components (filters, services, controllers)

---

### 2. **RedisConfig.java** (Configuration)
```java
@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }
}
```
- **Purpose**: Sets up Redis connection
- **StringRedisTemplate**: Allows executing Redis commands and scripts from Java
- Automatically reads Redis host/port from `application.properties`

---

### 3. **RateLimitFilter.java** (HTTP Filter - First Checkpoint)
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain chain) {
        // 1. Extract API Key
        String apiKey = request.getHeader("X-API-KEY");
        
        // 2. Check if API Key exists
        if (apiKey == null) {
            response.setStatus(401);  // Unauthorized
            response.getWriter().write("Unauthorized access, add api key");
            return;  // Stop here
        }
        
        // 3. Check rate limit
        if (!limiter.allowRequest(apiKey)) {
            response.setStatus(429);  // Too Many Requests
            response.getWriter().write("Too Many Requests");
            return;  // Stop here
        }
        
        // 4. Allow request to continue
        chain.doFilter(request, response);
    }
}
```

**Key Points:**
- Runs **before every request** reaches the controller
- `OncePerRequestFilter` ensures it runs exactly once per request
- Two checks:
  1. **Authentication**: API key must exist
  2. **Rate Limiting**: Check if user has tokens available
- If either check fails, request is blocked immediately

---

### 4. **RateLimiterService.java** (Core Logic)
```java
@Service
public class RateLimiterService {
    
    private static final int CAPACITY = 10;        // Max tokens
    private static final int REFILL_RATE = 5;      // Tokens per second
    
    public boolean allowRequest(String keyId) {
        String key = "rate_limiter:" + keyId;  // Redis key: "rate_limiter:user1"
        
        try {
            // Execute Lua script on Redis
            Long result = redisTemplate.execute(
                script,
                List.of(key),                          // Redis key
                String.valueOf(CAPACITY),              // Argument 1: capacity
                String.valueOf(REFILL_RATE),           // Argument 2: refill rate
                String.valueOf(System.currentTimeMillis())  // Argument 3: current time
            );
            
            return result != null && result == 1;  // 1 = allowed, 0 = blocked
        } catch (Exception e) {
            // Fail-open strategy: if Redis fails, allow request
            return true;
        }
    }
}
```

**Important Features:**
- **keyId**: Unique identifier for each user (extracted from API key)
- **Redis Key**: `"rate_limiter:user1"` stores tokens & last refill time
- **Lua Script**: Ensures atomic operation (no race conditions in distributed system)
- **Fail-open**: If Redis unavailable → allow request anyway (fail safely)

---

### 5. **RedisLuaScript.java** (The Algorithm Implementation)

This is where the **magic happens**! It's a Lua script that runs atomically on Redis.

```lua
local key = KEYS[1]                    -- e.g., "rate_limiter:user1"
local capacity = tonumber(ARGV[1])     -- e.g., 10
local refill_rate = tonumber(ARGV[2])  -- e.g., 5 tokens/sec
local now = tonumber(ARGV[3])          -- e.g., current timestamp in ms

-- Fetch current state from Redis
local data = redis.call("HMGET", key, "tokens", "last_refill_time")
local tokens = tonumber(data[1])       -- Current token count
local last_refill = tonumber(data[2])  -- When tokens were last refilled

-- Initialize if first request
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- Calculate new tokens based on elapsed time
local delta = math.max(0, now - last_refill)     -- milliseconds elapsed
local refill = delta * refill_rate / 1000        -- convert ms to seconds
tokens = math.min(capacity, tokens + refill)     -- add refill, cap at max

-- Check if we can allow the request
local allowed = 0
if tokens >= 1 then
    allowed = 1
    tokens = tokens - 1  -- Consume one token
end

-- Store updated state back to Redis
redis.call("HMSET", key,
    "tokens", tokens,
    "last_refill_time", now)

-- Set expiry: if no request for 1 hour, delete from Redis (cleanup)
redis.call("EXPIRE", key, 3600)

return allowed  -- 1 = success, 0 = blocked
```

**Why Lua Script?**
- **Atomic**: All operations execute as one unit
- **No race conditions**: Two requests can't both consume the same token
- **Fast**: Runs on Redis server, no network overhead

**Example Execution:**
```
Time: 1000ms, User1 makes request
  → tokens = 10 (capacity), last_refill = 1000
  → delta = 0, no refill
  → tokens = 10, allowed = 1 (consume 1 token)
  → Store: tokens = 9, last_refill = 1000

Time: 1200ms, User1 makes 5 requests (one after another)
  Request 1: tokens = 9 → 8 (allowed)
  Request 2: tokens = 8 → 7 (allowed)
  Request 3: tokens = 7 → 6 (allowed)
  Request 4: tokens = 6 → 5 (allowed)
  Request 5: tokens = 5 → 4 (allowed)

Time: 3000ms, User1 makes request
  → delta = 3000 - 1200 = 1800ms = 1.8 seconds
  → refill = 1.8 * 5 = 9 tokens
  → tokens = min(10, 4 + 9) = 10 (refilled to max)
  → tokens = 10 → 9 (allowed)
```

---

### 6. **DemoController.java** (Simple API Endpoint)
```java
@RestController
@RequestMapping("/api")
public class DemoController {
    
    @GetMapping("/data")
    public String data() {
        return "Success";
    }
}
```
- **Purpose**: Simple test endpoint
- **URL**: `GET /api/data`
- **Response**: "Success" (only if rate limit allows)

---

### 7. **application.properties** (Configuration)
```properties
spring.application.name=rateLimiter
server.port=8080
spring.redis.host=localhost
spring.redis.port=6379
```
- **Application name**: rateLimiter
- **Server runs on**: http://localhost:8080
- **Redis connection**: localhost:6379

---

## 🧪 Testing the Flow

### Test Case 1: Missing API Key
```bash
curl -i http://localhost:8080/api/data
```
**Response:**
```
HTTP/1.1 401 Unauthorized
Body: "Unauthorized access, add api key"
```

### Test Case 2: Valid API Key (First 10 Requests)
```bash
curl -i -H "X-API-KEY: user1" http://localhost:8080/api/data
```
**Response:**
```
HTTP/1.1 200 OK
Body: "Success"
```

### Test Case 3: Rate Limit Exceeded
```bash
# Make 11 requests rapidly
for i in {1..11}; do
  curl -i -H "X-API-KEY: user1" http://localhost:8080/api/data
done
```
**Response for request 11:**
```
HTTP/1.1 429 Too Many Requests
Body: "Too Many Requests"
```

---

## 🏗️ Architecture Decisions

### 1. **Redis as Centralized Store**
- ✅ Works across multiple application instances
- ✅ Single source of truth for token counts
- ✅ No need to synchronize between servers

### 2. **Lua Script for Atomicity**
- ✅ Read-modify-write happens as one transaction
- ✅ No possibility of two concurrent requests both consuming same token
- ✅ Prevents race conditions in distributed environment

### 3. **Fail-Open Strategy**
- ✅ If Redis crashes, app keeps running
- ✅ Better UX than blocking all traffic
- ✅ Trades some rate limiting for availability

### 4. **HTTP Filter for Enforcement**
- ✅ Rate limiting happens before controller logic
- ✅ Reduces load on business logic
- ✅ Works globally for all endpoints

### 5. **TTL (Time-To-Live) on Redis Keys**
- ✅ Automatic cleanup after 1 hour of inactivity
- ✅ Prevents unbounded Redis memory growth
- ✅ No manual maintenance needed

---

## 📊 Configuration Parameters

Located in `RateLimiterService.java`:

```java
private static final int CAPACITY = 10;        // Each user gets 10 tokens
private static final int REFILL_RATE = 5;      // Refill 5 tokens per second
```

### Calculations:
- **Capacity**: 10 requests per refill cycle
- **Refill Rate**: 5 tokens/second
- **Time to refill from 0 to 10**: 10 ÷ 5 = **2 seconds**
- **Max sustained rate**: 5 requests/second

### Change Examples:
```java
// Stricter: Allow only 3 requests per second
CAPACITY = 10;
REFILL_RATE = 3;

// Looser: Allow 20 requests per second
CAPACITY = 20;
REFILL_RATE = 20;
```

---

## 🚀 Dependencies

```xml
<!-- Spring Boot Web: For HTTP endpoints & filters -->
<spring-boot-starter-web>

<!-- Spring Data Redis: For Redis integration & Lua script execution -->
<spring-boot-starter-data-redis>

<!-- Spring Boot Starter: Core Spring functionality -->
<spring-boot-starter>
```

---

## 📋 Summary: Key Takeaways

| Component | Purpose |
|-----------|---------|
| **RateLimitFilter** | Intercepts requests, checks API key & rate limit |
| **RateLimiterService** | Calls Redis Lua script, returns allow/block decision |
| **RedisLuaScript** | Token Bucket algorithm executed atomically on Redis |
| **RedisConfig** | Configures Redis connection |
| **DemoController** | Simple endpoint to test rate limiting |

**The Flow:**
```
Request → Filter checks API Key → Filter calls RateLimiterService
→ Service executes Lua Script on Redis → Redis returns 1 or 0
→ Filter allows/blocks request → Response sent to client
```

---

## 🔐 Security & Production Considerations

1. **API Key Management**: Currently hardcoded, should use a database in production
2. **Key Rotation**: No mechanism, implement regular key rotation
3. **Monitoring**: Add metrics tracking (blocked requests, latency)
4. **Logging**: Add proper logging for debugging and auditing
5. **Different Limits**: Different limits for different user tiers/endpoints
6. **Redis Persistence**: Enable RDB/AOF for data durability

---

## 🎓 Learning Value

This project teaches:
- ✅ Token Bucket algorithm implementation
- ✅ Redis Lua scripting for atomic operations
- ✅ Distributed system design (handling multiple instances)
- ✅ Spring Boot filters and request interception
- ✅ Fail-open strategies for resilience
- ✅ Performance considerations in high-concurrency scenarios


