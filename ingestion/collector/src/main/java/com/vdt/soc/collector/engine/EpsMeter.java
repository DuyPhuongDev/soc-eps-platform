package com.vdt.soc.collector.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class EpsMeter {

    private static final String KEY_OK = "eps:ok:";
    private static final String KEY_DROP = "eps:drop:";


    private static final Duration TTL_OK = Duration.ofSeconds(120);
    private static final Duration TTL_DROP = Duration.ofSeconds(172800);

    private final ReactiveRedisTemplate<String, String> redisTemplate;


    public Mono<Void> recordAccepted(UUID tenantId) {
        return recordAccepted(tenantId, 1);
    }

    public Mono<Void> recordDropped(UUID tenantId) {
        return recordDropped(tenantId, 1);
    }


    public Mono<Void> recordAccepted(UUID tenantId, int count) {
        if (count <= 0) {
            return Mono.empty();
        }
        String field = epochSecond();
        return incrementAndExpire(KEY_OK + tenantId, field, count, TTL_OK);
    }


    public Mono<Void> recordDropped(UUID tenantId, int count) {
        if (count <= 0) {
            return Mono.empty();
        }
        String field = epochSecond();
        return incrementAndExpire(KEY_DROP + tenantId, field, count, TTL_DROP);
    }


    private Mono<Void> incrementAndExpire(String key, String field, int count, Duration ttl) {
        return redisTemplate.<String, String>opsForHash()
                .increment(key, field, count)
                .then(redisTemplate.expire(key, ttl))
                .then()
                .onErrorResume(e -> {
                    log.warn("EPS metering error for key={}: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    private static String epochSecond() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }
}
