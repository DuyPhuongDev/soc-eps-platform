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

/**
 * Periodically refreshes API key cache (from tenant-service) and policy cache
 * (from license-service), with snapshot fallback on each refresh cycle.
 * <p>
 * On startup ({@link #warmup()}), both caches are populated immediately so
 * the collector is ready to serve traffic without waiting for the first
 * scheduled refresh cycle.
 * <p>
 * When both etcd watchers ({@code EtcdPolicyWatcher}, {@code EtcdApiKeyWatcher})
 * are healthy, the scheduled HTTP poll is skipped — cache updates arrive
 * via etcd watch in real time. If either watcher disconnects, the poll
 * resumes as a fallback.
 * <p>
 * Runs on scheduler thread (not Netty event loop), so blocking
 * {@code WebClient.block()} in {@code @Scheduled} is safe.
 */
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
    private final SnapshotManager snapshotManager;
    private final InternalApiProperties internalApi;
    private final CacheProperties cacheProperties;
    private final WebClient.Builder webClientBuilder;

    /**
     * Tracks etcd watch health across both watchers.
     * Only when both watchers are connected (count == 2) does the scheduled
     * HTTP poll get skipped. Each watcher increments on connect, decrements
     * on disconnect.
     */
    private final AtomicInteger healthyWatchCount = new AtomicInteger(0);
    private static final int TOTAL_WATCHERS = 2;

    /**
     * Populate both caches immediately on startup.
     * Failures are logged but do NOT prevent the application from starting —
     * the next scheduled refresh (30s) will retry.
     * <p>
     * Wrapped in try-catch because {@code @PostConstruct} exceptions
     * kill the application context.
     */
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

    /**
     * Called by etcd watchers when their watch connection is established.
     */
    public void onWatchConnected() {
        int count = healthyWatchCount.incrementAndGet();
        log.info("etcd watch connected ({} of {} watchers healthy)", count, TOTAL_WATCHERS);
    }

    /**
     * Called by etcd watchers when their watch connection is lost
     * and a retry has been scheduled.
     */
    public void onWatchDisconnected() {
        int count = healthyWatchCount.decrementAndGet();
        log.warn("etcd watch disconnected ({} of {} watchers healthy)", count, TOTAL_WATCHERS);
    }

    private boolean isWatchHealthy() {
        return healthyWatchCount.get() >= TOTAL_WATCHERS;
    }

    /**
     * Scheduled fallback refresh for both caches.
     * <p>
     * Primary path is etcd watch ({@link EtcdPolicyWatcher}, {@link EtcdApiKeyWatcher}).
     * This poll runs only when the etcd watch is not fully healthy.
     */
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
                    .uri(internalApi.getTenantServiceUrl() + "/api/v1/internal/api-keys")
                    .header("X-Internal-Secret", internalApi.getInternalSecret())
                    .retrieve()
                    .bodyToMono(API_KEYS_TYPE)
                    .block(); // intentional block — runs on scheduler thread

            if (mappings != null && !mappings.isEmpty()) {
                apiKeyCache.replaceAll(mappings);
                snapshotManager.writeApiKeys(mappings);
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

    // ── Policy cache ───────────────────────────────────────────────────

    private void refreshPolicies() {
        try {
            List<PolicyDTO> policies = webClientBuilder.build()
                    .get()
                    .uri(internalApi.getLicenseServiceUrl() + "/api/v1/licenses/internal/policies")
                    .header("X-Internal-Secret", internalApi.getInternalSecret())
                    .retrieve()
                    .bodyToMono(POLICIES_TYPE)
                    .block(); // intentional block — runs on scheduler thread

            if (policies != null && !policies.isEmpty()) {
                policyCache.replaceAll(policies);
                snapshotManager.writePolicies(policies);
            } else {
                log.warn("License-service returned empty policy list, keeping current cache");
            }
        } catch (Exception e) {
            log.warn("Failed to refresh policies from license-service: {}. Trying snapshot fallback...",
                    e.getMessage());
            List<PolicyDTO> fallback = snapshotManager.readPolicies();
            if (!fallback.isEmpty()) {
                policyCache.replaceAll(fallback);
                log.info("Policy cache restored from snapshot ({} entries)", fallback.size());
            } else {
                log.warn("No policy snapshot available, keeping current cache ({} entries)",
                        policyCache.size());
            }
        }
    }
}
