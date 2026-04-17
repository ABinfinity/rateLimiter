package com.example.rateLimiter;
// Uses Token Bucket algorithm to implement rate limiting using Redis
//Lua script and sprint boot application
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateLimiterApplication.class, args);
		System.out.println("hello");
	}

}
