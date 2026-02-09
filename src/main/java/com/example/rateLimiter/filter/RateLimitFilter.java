package com.example.rateLimiter.filter;

import com.example.rateLimiter.limiter.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService limiter;

    public RateLimitFilter(RateLimiterService limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        System.out.println("METHOD=" + request.getMethod() + " URI=" + request.getRequestURI() + " API_KEY=" + request.getHeader("X-API-KEY"));
        System.out.println("hello");

        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey == null) {
            response.setStatus(401);
            response.getWriter().write("Unauthorized access, add api key");
            return;
        }

//        if (!limiter.allowRequest(apiKey)) {
//            response.setStatus(429);
//            response.getWriter().write("Too Many Requests");
//            return;
//        }

        chain.doFilter(request, response);
    }
}

