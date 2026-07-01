package com.vdt.soc.collector.cache;

import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory cache of API key hash → tenantId mappings.
 * <p>
 * Populated by {@link EtcdApiKeyWatcher} via etcd watch (real-time)
 * with scheduled poll fallback in {@link CacheRefresher}.
 * <p>
 * Thread-safe: uses {@link ConcurrentHashMap} — read path is lock-free O(1).
 * Multi-instance sync is handled by etcd watch — every collector instance
 * receives the same watch events and updates its local cache independently.
 */
@Slf4j
@Component
public class ApiKeyCache {

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
     * Replace all entries with a fresh set (used by scheduled poll fallback).
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

        log.info("ApiKeyCache replaced: {} active API keys", cache.size());
    }

    /**
     * Add or update a single API key mapping (used by etcd watch).
     */
    public void put(String apiKeyHash, UUID tenantId) {
        cache.put(apiKeyHash, tenantId);
        log.debug("ApiKeyCache put: hash={}, tenant={}", apiKeyHash, tenantId);
    }

    /**
     * Remove a single API key mapping (used by etcd watch on revoke).
     */
    public void remove(String apiKeyHash) {
        cache.remove(apiKeyHash);
        log.debug("ApiKeyCache remove: hash={}", apiKeyHash);
    }

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
