package com.vdt.soc.collector.config;

import com.vdt.soc.collector.engine.TokenBucketEngine;
import com.vdt.soc.collector.engine.TokenBucketRedis;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Collector-specific Redis configuration.
 * <p>
 * The {@code ReactiveRedisTemplate<String, String>} bean is provided by
 * Spring Boot's {@code RedisReactiveAutoConfiguration} via
 * {@code ReactiveStringRedisTemplate} (which extends {@code ReactiveRedisTemplate<String, String>}).
 * <p>
 * This config registers only the Token Bucket Lua script and the
 * {@link TokenBucketEngine} implementation.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    @Bean
    public RedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public TokenBucketEngine tokenBucketEngine(ReactiveRedisTemplate<String, String> redisTemplate,
                                               RedisScript<List> tokenBucketScript) {
        return new TokenBucketRedis(redisTemplate, tokenBucketScript);
    }
}
