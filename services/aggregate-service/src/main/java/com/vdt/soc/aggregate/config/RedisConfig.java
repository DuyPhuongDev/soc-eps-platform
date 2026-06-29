package com.vdt.soc.aggregate.config;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Aggregate Service Redis configuration.
 * <p>
 * Uses blocking {@link StringRedisTemplate} — adequate for scheduled jobs
 * and low-volume dashboard API calls. Not reactive.
 * <p>
 * Connection factory is auto-configured by Spring Boot from
 * {@link RedisProperties} (spring.data.redis.*).
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}