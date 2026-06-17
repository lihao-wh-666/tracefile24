package com.hotevent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotevent.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.google.common.util.concurrent.RateLimiter;

@Slf4j
@Component
public class PublicApiRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicApiRateLimitInterceptor(
            @Value("${hot-event.public-api.rate-limit-permits-per-second:10.0}") double permitsPerSecond) {
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
        log.info("Public API rate limiter initialized: {} permits/second", permitsPerSecond);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!rateLimiter.tryAcquire()) {
            log.warn("Public API rate limit exceeded for request: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            Result<Void> result = Result.error(429, "请求过于频繁，请稍后再试");
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }
        return true;
    }
}
