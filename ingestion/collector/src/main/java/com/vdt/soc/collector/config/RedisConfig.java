package com.vdt.soc.collector.config;

import com.vdt.soc.collector.engine.QuotaEnforcer;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public RedisScript<List<Object>> quotaCheckScript() {
        DefaultRedisScript<List<Object>> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/quota_check.lua"));
        script.setResultType((Class) List.class);
        return script;
    }

    @Bean
    public TokenBucketEngine tokenBucketEngine(ReactiveRedisTemplate<String, String> redisTemplate,
                                               RedisScript<List> tokenBucketScript) {
        return new TokenBucketRedis(redisTemplate, tokenBucketScript);
    }

    @Bean
    public QuotaEnforcer quotaEnforcer(ReactiveRedisTemplate<String, String> redisTemplate,
                                       RedisScript<List<Object>> quotaCheckScript) {
        return new QuotaEnforcer(redisTemplate, quotaCheckScript);
    }
}
