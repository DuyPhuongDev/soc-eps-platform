package com.vdt.soc.collector.cache;

import com.vdt.soc.common.model.dto.PolicyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory cache of active license policies.
 * Keyed by tenantId for O(1) lookup during event enforcement.
 * <p>
 * Thread-safe: uses ConcurrentHashMap — no lock contention on read path.
 * Refresh replaces the entire map atomically via replaceAll().
 */
@Slf4j
@Component
public class PolicyCache {

    private final Map<UUID, PolicyDTO> cache = new ConcurrentHashMap<>();

    /**
     * Look up the policy for a given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the policy, or PolicyDTO.DEFAULT if not cached
     */
    public PolicyDTO get(UUID tenantId) {
        return cache.getOrDefault(tenantId, PolicyDTO.DEFAULT);
    }

    /**
     * Replace all entries with a fresh set from license-service poll.
     * Builds a new map and swaps it atomically so concurrent readers
     * never see a partially-built map.
     *
     * @param policies collection of active policies
     */
    public void replaceAll(Collection<PolicyDTO> policies) {
        Map<UUID, PolicyDTO> newMap = policies.stream()
                .filter(p -> p.getTenantId() != null)
                .collect(Collectors.toMap(
                        PolicyDTO::getTenantId,
                        Function.identity(),
                        (existing, replacement) -> replacement));
        cache.replaceAll((k, v) -> newMap.getOrDefault(k, v));

        // Remove tenants no longer in the fresh set, add new ones
        cache.keySet().retainAll(newMap.keySet());
        cache.putAll(newMap);

        log.info("PolicyCache refreshed: {} active policies", cache.size());
    }

    /**
     * @return current number of cached policies
     */
    public int size() {
        return cache.size();
    }

    /**
     * @return a snapshot copy of all cached entries (for snapshot persistence)
     */
    public Collection<PolicyDTO> snapshot() {
        return Map.copyOf(cache).values();
    }
}
