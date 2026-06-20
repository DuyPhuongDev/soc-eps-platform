package com.vdt.soc.collector.cache;

import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory cache of API key hash → tenantId mappings.
 * <p>
 * The hash is SHA-256 hex of the raw API key. This way the cache
 * never stores plaintext keys, and lookups are fast O(1) via ConcurrentHashMap.
 * <p>
 * Thread-safe: uses ConcurrentHashMap — no lock contention on read path.
 */
@Slf4j
@Component
public class ApiKeyCache {

    /**
     * key = sha256(apiKey), value = tenantId
     */
    private final Map<String, UUID> cache = new ConcurrentHashMap<>();

    /**
     * Resolve a SHA-256 API key hash to a tenant ID.
     *
     * @param apiKeyHash SHA-256 hex of the raw API key
     * @return tenantId, or null if not found
     */
    public UUID resolve(String apiKeyHash) {
        return cache.get(apiKeyHash);
    }

    /**
     * Replace all entries with a fresh set from tenant-service poll.
     *
     * @param mappings collection of {tenantId, apiKeyHash} pairs
     */
    public void replaceAll(Collection<TenantApiKeyMapping> mappings) {
        Map<String, UUID> newMap = mappings.stream()
                .filter(m -> m.getApiKeyHash() != null && m.getTenantId() != null)
                .collect(Collectors.toMap(
                        TenantApiKeyMapping::getApiKeyHash,
                        TenantApiKeyMapping::getTenantId,
                        (existing, replacement) -> replacement));

        cache.keySet().retainAll(newMap.keySet());
        cache.putAll(newMap);

        log.info("ApiKeyCache refreshed: {} active API keys", cache.size());
    }

    /**
     * @return current number of cached API keys
     */
    public int size() {
        return cache.size();
    }

    /**
     * @return a snapshot copy of all entries (for snapshot persistence)
     */
    public Collection<TenantApiKeyMapping> snapshot() {
        return cache.entrySet().stream()
                .map(e -> TenantApiKeyMapping.builder()
                        .apiKeyHash(e.getKey())
                        .tenantId(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}
