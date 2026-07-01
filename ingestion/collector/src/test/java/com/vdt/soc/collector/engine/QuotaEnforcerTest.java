package com.vdt.soc.collector.engine;

import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.core.enumeration.LicenseMode;
import com.vdt.soc.common.core.enumeration.LicensePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaEnforcerTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List<Object>> quotaScript;

    @InjectMocks
    private QuotaEnforcer quotaEnforcer;

    private UUID tenantId;
    private PolicyDTO policy;
    private Instant startDate;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        startDate = Instant.now().minusSeconds(3600); // 1 hour ago
        policy = PolicyDTO.builder()
                .tenantId(tenantId)
                .plan(LicensePlan.STARTER)
                .epsQuota(100)
                .monthlyQuota(3_000_000L)
                .mode(LicenseMode.THROTTLE)
                .burstMultiplier(1.0)
                .validFrom(startDate)
                .validUntil(Instant.now().plusSeconds(86400 * 365))
                .build();
    }

    @Test
    void shouldAllowWhenUnderQuota() {
        // Lua script returns the requested count → allowed
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(5L)));

        StepVerifier.create(quotaEnforcer.tryConsume(tenantId, policy, startDate, 5))
                .assertNext(allowed -> assertThat(allowed).isEqualTo(5L))
                .verifyComplete();
    }

    @Test
    void shouldDenyWhenQuotaExceeded() {
        // Lua script returns 0 → quota exceeded
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L)));

        StepVerifier.create(quotaEnforcer.tryConsume(tenantId, policy, startDate, 10))
                .assertNext(allowed -> assertThat(allowed).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    void shouldDenyForDefaultPolicy() {
        StepVerifier.create(quotaEnforcer.tryConsume(tenantId, PolicyDTO.DEFAULT, startDate, 1))
                .assertNext(allowed -> assertThat(allowed).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    void shouldDenyWhenRequestedIsZero() {
        StepVerifier.create(quotaEnforcer.tryConsume(tenantId, policy, startDate, 0))
                .assertNext(allowed -> assertThat(allowed).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    void shouldFailOpenOnRedisError() {
        // Redis error → fail open, allow all requested
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection refused")));

        StepVerifier.create(quotaEnforcer.tryConsume(tenantId, policy, startDate, 3))
                .assertNext(allowed -> assertThat(allowed).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    void shouldComputeMonthlyWindowKey() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);

        when(redisTemplate.execute(any(RedisScript.class), keysCaptor.capture(), anyList()))
                .thenReturn(Flux.just(List.of(1L)));

        quotaEnforcer.tryConsume(tenantId, policy, startDate, 1).block();

        List<String> keys = keysCaptor.getValue();
        assertThat(keys).hasSize(1);
        // Monthly key: quota:monthly:{tenantId}:{window}
        assertThat(keys.get(0)).startsWith("quota:monthly:" + tenantId + ":");
    }

    @Test
    void shouldPassQuotaLimitAsArg() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), argsCaptor.capture()))
                .thenReturn(Flux.just(List.of(1L)));

        quotaEnforcer.tryConsume(tenantId, policy, startDate, 7).block();

        List<String> args = argsCaptor.getValue();
        assertThat(args).hasSize(3);
        assertThat(args.get(0)).isEqualTo("3000000"); // monthlyQuota
        assertThat(args.get(1)).isEqualTo("7");        // requested
    }
}
