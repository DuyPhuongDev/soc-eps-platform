package com.vdt.soc.aggregate.cache;

import com.vdt.soc.common.core.dto.PolicyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PolicyCache {

    private final Map<UUID, PolicyDTO> cache = new ConcurrentHashMap<>();


    public PolicyDTO get(UUID tenantId) {
        return cache.getOrDefault(tenantId, PolicyDTO.DEFAULT);
    }

    public void replaceAll(Collection<PolicyDTO> policies) {
        Map<UUID, PolicyDTO> newMap = policies.stream()
                .filter(p -> p.getTenantId() != null)
                .collect(Collectors.toMap(
                        PolicyDTO::getTenantId,
                        Function.identity(),
                        (existing, replacement) -> replacement));
        cache.keySet().retainAll(newMap.keySet());
        cache.putAll(newMap);
        log.info("PolicyCache refreshed: {} active policies", cache.size());
    }

    public int size() {
        return cache.size();
    }

    public void put(PolicyDTO policy) {
        if (policy.getTenantId() != null) {
            cache.put(policy.getTenantId(), policy);
            log.debug("PolicyCache upsert: tenant={}, plan={}, eps={}",
                    policy.getTenantId(), policy.getPlan(), policy.getEpsQuota());
        }
    }

    public void remove(UUID tenantId) {
        cache.remove(tenantId);
        log.debug("PolicyCache remove: tenant={}", tenantId);
    }

    public Collection<PolicyDTO> snapshot() {
        return Map.copyOf(cache).values();
    }
}