package com.microsoft.samples.ratelimitingapi;

import com.microsoft.samples.ratelimitingapi.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class RateLimitConfiguration {

    private final RateLimitConfig rateLimitConfig;

    public RateLimitConfiguration(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Bean
    public Bucket rateLimiter() {
        int capacity = rateLimitConfig.getRatelimit().getCapacity();
        int duration = rateLimitConfig.getRatelimit().getDuration();

        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofMinutes(duration)));
        return Bucket.builder().addLimit(limit).build();
    }
}
