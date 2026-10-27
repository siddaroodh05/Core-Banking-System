package com.siddu.apigateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class UserRateLimiter {

    @Value("${rate-limit.max-requests}")
    private long MAX_REQUESTS;
    @Value("${rate-limit.window}")
    private  Duration WINDOW ;

    private final StringRedisTemplate redisTemplate;

    public UserRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(UUID userId) {

        String key = "rate-limit:user:" + userId;

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            return false;
        }

        if (count == 1) {
            redisTemplate.expire(key, WINDOW);
        }

        return count <= MAX_REQUESTS;
    }
}