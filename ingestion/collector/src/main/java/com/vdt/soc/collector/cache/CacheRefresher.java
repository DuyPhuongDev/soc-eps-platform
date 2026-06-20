package com.vdt.soc.collector.cache;

import com.vdt.soc.collector.config.CacheProperties;
import com.vdt.soc.collector.config.InternalApiProperties;
import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Periodically refreshes in-memory API key cache from tenant-service.
 * <p>
 * Policy cache is now populated via etcd watch (see EtcdPolicyWatcher),
 * so this class only refreshes API key mappings.
 * <p>
 * Runs on a scheduler thread (not Netty event loop), so blocking
 * WebClient.subscribe() in @Scheduled is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheRefresher {

    private static final ParameterizedTypeReference<List<TenantApiKeyMapping>> API_KEYS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private final ApiKeyCache apiKeyCache;
    private final SnapshotManager snapshotManager;
    private final InternalApiProperties internalApi;
    private final CacheProperties cacheProperties;
    private final WebClient.Builder webClientBuilder;

    @Scheduled(fixedDelayString = "${app.cache.refresh-interval-seconds:30}000")
    public void refreshAll() {
        // Policy cache is now populated via etcd watch (see EtcdPolicyWatcher)
        refreshApiKeys();
    }

    private void refreshApiKeys() {
        try {
            List<TenantApiKeyMapping> mappings = webClientBuilder.build()
                    .get()
                    .uri(internalApi.getTenantServiceUrl() + "/api/v1/internal/api-keys")
                    .header("X-Internal-Secret", internalApi.getInternalSecret())
                    .retrieve()
                    .bodyToMono(API_KEYS_TYPE)
                    .block(); // intentional block — runs on scheduler thread

            if (mappings != null && !mappings.isEmpty()) {
                apiKeyCache.replaceAll(mappings);
                snapshotManager.writeApiKeys(List.copyOf(apiKeyCache.snapshot()));
            } else {
                log.warn("Tenant-service returned empty API key list, keeping current cache");
            }
        } catch (Exception e) {
            log.warn("Failed to refresh API keys from tenant-service: {}. Trying snapshot fallback...",
                    e.getMessage());
            List<TenantApiKeyMapping> fallback = snapshotManager.readApiKeys();
            if (!fallback.isEmpty()) {
                apiKeyCache.replaceAll(fallback);
                log.info("API key cache restored from snapshot ({} entries)", fallback.size());
            } else {
                log.warn("No API key snapshot available, keeping current cache ({} entries)",
                        apiKeyCache.size());
            }
        }
    }
}
