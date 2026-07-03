package com.vdt.soc.collector.cache;

import com.vdt.soc.collector.config.CacheProperties;
import com.vdt.soc.collector.config.InternalApiProperties;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheRefresher {

    private static final ParameterizedTypeReference<List<TenantApiKeyMapping>> API_KEYS_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<PolicyDTO>> POLICIES_TYPE =
            new ParameterizedTypeReference<>() {};

    private final ApiKeyCache apiKeyCache;
    private final PolicyCache policyCache;
    private final InternalApiProperties internalApi;
    private final CacheProperties cacheProperties;
    private final WebClient.Builder webClientBuilder;

    private final AtomicInteger healthyWatchCount = new AtomicInteger(0);
    private static final int TOTAL_WATCHERS = 2;

    @PostConstruct
    public void warmup() {
        log.info("Warming up caches on startup...");
        try {
            refreshAll();
        } catch (Exception e) {
            log.warn("Cache warmup failed (services may not be ready yet): {}. "
                    + "Scheduled refresh will retry in {}s.",
                    e.getMessage(), cacheProperties.getRefreshIntervalSeconds());
        }
    }

    public void onWatchConnected() {
        int count = healthyWatchCount.incrementAndGet();
        log.info("etcd watch connected ({} of {} watchers healthy)", count, TOTAL_WATCHERS);
    }

    public void onWatchDisconnected() {
        int count = healthyWatchCount.decrementAndGet();
        log.warn("etcd watch disconnected ({} of {} watchers healthy)", count, TOTAL_WATCHERS);
    }

    private boolean isWatchHealthy() {
        return healthyWatchCount.get() >= TOTAL_WATCHERS;
    }

    @Scheduled(fixedDelayString = "${app.cache.refresh-interval-seconds:30}000")
    public void refreshAll() {
        if (isWatchHealthy()) {
            log.debug("etcd watch healthy, skipping HTTP poll");
            return;
        }
        log.info("etcd watch not fully healthy, running HTTP poll fallback");
        refreshApiKeys();
        refreshPolicies();
    }

    // ── API Key cache ──────────────────────────────────────────────────

    private void refreshApiKeys() {
        try {
            List<TenantApiKeyMapping> mappings = webClientBuilder.build()
                    .get()
                    .uri(internalApi.getTenantServiceUrl() + internalApi.getApiKeyUri())
                    .header("X-Internal-Secret", internalApi.getInternalSecret())
                    .retrieve()
                    .bodyToMono(API_KEYS_TYPE)
                    .block();

            if (mappings != null && !mappings.isEmpty()) {
                apiKeyCache.replaceAll(mappings);
            } else {
                log.warn("Tenant-service returned empty API key list, keeping current cache");
            }
        } catch (Exception e) {
            log.error("Failed to refresh API keys from tenant-service: {}",
                    e.getMessage());
        }
    }

    // ── Policy cache ───────────────────────────────────────────────────

    private void refreshPolicies() {
        try {
            List<PolicyDTO> policies = webClientBuilder.build()
                    .get()
                    .uri(internalApi.getLicenseServiceUrl() + internalApi.getPolicyUri())
                    .header("X-Internal-Secret", internalApi.getInternalSecret())
                    .retrieve()
                    .bodyToMono(POLICIES_TYPE)
                    .block(); // intentional block — runs on scheduler thread

            if (policies != null && !policies.isEmpty()) {
                policyCache.replaceAll(policies);
            } else {
                log.warn("License-service returned empty policy list, keeping current cache");
            }
        } catch (Exception e) {
            log.error("Failed to refresh policies from license-service: {}.",
                    e.getMessage());
        }
    }
}
