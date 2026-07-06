package com.vdt.soc.aggregate.cache;

import com.vdt.soc.common.core.dto.PolicyDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCacheTest {

    private PolicyCache cache;

    @BeforeEach
    void setUp() {
        cache = new PolicyCache();
    }

    @Test
    void get_returnsDefaultForUnknownTenant() {
        assertThat(cache.get(UUID.randomUUID())).isEqualTo(PolicyDTO.DEFAULT);
    }

    @Test
    void put_and_get() {
        UUID tenantId = UUID.randomUUID();
        PolicyDTO policy = PolicyDTO.builder()
                .tenantId(tenantId)
                .epsQuota(100)
                .build();
        cache.put(policy);
        assertThat(cache.get(tenantId).getEpsQuota()).isEqualTo(100);
    }

    @Test
    void remove_evictsTenant() {
        UUID tenantId = UUID.randomUUID();
        PolicyDTO policy = PolicyDTO.builder().tenantId(tenantId).build();
        cache.put(policy);
        cache.remove(tenantId);
        assertThat(cache.get(tenantId)).isEqualTo(PolicyDTO.DEFAULT);
    }

    @Test
    void replaceAll_replacesEntireCache() {
        UUID t1 = UUID.randomUUID();
        cache.put(PolicyDTO.builder().tenantId(t1).build());
        cache.replaceAll(List.of());
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    void snapshot_returnsImmutableCopy() {
        UUID t1 = UUID.randomUUID();
        cache.put(PolicyDTO.builder().tenantId(t1).build());
        assertThat(cache.snapshot()).hasSize(1);
    }

    @Test
    void replaceAll_withMultiplePolicies() {
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        cache.put(PolicyDTO.builder().tenantId(t1).epsQuota(50).build());
        cache.replaceAll(List.of(
                PolicyDTO.builder().tenantId(t2).epsQuota(100).build()
        ));
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get(t2).getEpsQuota()).isEqualTo(100);
        assertThat(cache.get(t1)).isEqualTo(PolicyDTO.DEFAULT);
    }

    @Test
    void put_ignoresNullTenantId() {
        PolicyDTO policy = PolicyDTO.builder().tenantId(null).build();
        cache.put(policy);
        assertThat(cache.size()).isEqualTo(0);
    }
}
