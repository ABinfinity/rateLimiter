# Distributed Rate Limiter (Spring Boot + Redis)

## Overview

This project implements a **distributed rate limiter** using **Spring Boot** and **Redis**, based on the **Token Bucket algorithm**.

The rate limiter is designed to work correctly in a **horizontally scaled environment**, where multiple application instances handle requests concurrently. Redis is used as a centralized store, and **Lua scripting** ensures atomic read-modify-write operations under high concurrency.

The rate limiting logic is enforced at the **HTTP filter level**, similar to how API gateways apply global request throttling.

---

## Key Features

- Distributed rate limiting across multiple service instances
- Token Bucket algorithm with configurable capacity and refill rate
- Redis-backed state with automatic cleanup using TTL
- Atomic enforcement using Redis Lua scripts
- Fail-open strategy when Redis is unavailable
- Proper HTTP status codes (`401`, `429`)
- Production-style filter-based enforcement

---

## Rate Limiting Algorithm

### Token Bucket

Each client (identified by an API key) has a bucket:
- **Capacity**: Maximum number of tokens
- **Refill Rate**: Tokens added per second
- **Each request consumes 1 token**

If no token is available, the request is rejected with **HTTP 429 (Too Many Requests)**.

---

## Tech Stack

- Java 17
- Spring Boot
- Spring Web
- Spring Data Redis
- Redis
- Redis Lua scripting

---

## Prerequisites

- Java 17+
- Maven
- Redis (local or Docker)

---

## Running Redis

### Using Docker (Recommended)

```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### Or run locally

```bash
redis-server
```

---

## Running the Application

```bash
mvn spring-boot:run
```

Application runs at:

```
http://localhost:8080
```

---

## Sample Requests

### Authorized Request

```bash
curl -i -H "X-API-KEY: user1" http://localhost:8080/api/data
```

### Missing API Key

```bash
curl -i http://localhost:8080/api/data
```

Response: `401 Unauthorized`

### Rate Limit Exceeded

Response: `429 Too Many Requests`

---

## Notes

- Rate limiting is enforced using a servlet filter
- Redis Lua ensures atomic read-modify-write behavior
- TTL prevents unbounded Redis memory usage

---

## License

For learning and interview preparation purposes.
