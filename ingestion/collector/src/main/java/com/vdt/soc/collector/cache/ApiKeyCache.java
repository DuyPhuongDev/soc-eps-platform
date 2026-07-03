package com.vdt.soc.collector.cache;

import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
@Component
public class ApiKeyCache {

    private final Map<String, UUID> cache = new ConcurrentHashMap<>();

    public UUID resolve(String apiKeyHash) {
        return cache.get(apiKeyHash);
    }

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

    public void put(String apiKeyHash, UUID tenantId) {
        cache.put(apiKeyHash, tenantId);
        log.debug("ApiKeyCache put: hash={}, tenant={}", apiKeyHash, tenantId);
    }

    public void remove(String apiKeyHash) {
        cache.remove(apiKeyHash);
        log.debug("ApiKeyCache remove: hash={}", apiKeyHash);
    }

    public int size() {
        return cache.size();
    }


    public Collection<TenantApiKeyMapping> snapshot() {
        return cache.entrySet().stream()
                .map(e -> TenantApiKeyMapping.builder()
                        .apiKeyHash(e.getKey())
                        .tenantId(e.getValue())
                        .build())
                .toList();
    }
}
