package com.vdt.soc.collector.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import com.vdt.soc.common.etcd.EtcdWatchClient;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.watch.WatchEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EtcdApiKeyWatcher {

    private final EtcdWatchClient watchClient;
    private final ApiKeyCache apiKeyCache;
    private final CacheRefresher cacheRefresher;
    private final ObjectMapper objectMapper;
    private final String prefix;
    private final long watchRetryDelayMs;
    private volatile Watch.Watcher watcher;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("etcd-apikey-watch-retry-").factory());

    public EtcdApiKeyWatcher(EtcdWatchClient watchClient,
                             ApiKeyCache apiKeyCache,
                             CacheRefresher cacheRefresher,
                             ObjectMapper objectMapper,
                             @Value("${app.etcd.apikey-prefix:/apikeys/}") String prefix,
                             @Value("${app.etcd.watch-retry-delay-ms:3000}") long watchRetryDelayMs) {
        this.watchClient = watchClient;
        this.apiKeyCache = apiKeyCache;
        this.cacheRefresher = cacheRefresher;
        this.objectMapper = objectMapper;
        this.prefix = prefix;
        this.watchRetryDelayMs = watchRetryDelayMs;
    }

    @PostConstruct
    public void start() {
        log.info("Starting etcd API key watcher on prefix={}", prefix);
        loadInitial();
        startWatch();
    }

    @PreDestroy
    public void stop() {
        if (watcher != null) {
            watcher.close();
            log.info("etcd API key watcher closed");
        }
        retryExecutor.shutdownNow();
    }

    // ── Initial load ────────────────────────────────────────────────────

    private void loadInitial() {
        try {
            List<EtcdWatchClient.KeyValue> kvs = watchClient.getAll(prefix);
            if (kvs.isEmpty()) {
                log.info("No API keys found in etcd prefix={} — ApiKeyCache will be "
                        + "populated by scheduled poll fallback", prefix);
                return;
            }

            List<TenantApiKeyMapping> mappings = kvs.stream()
                    .map(kv -> deserialize(kv.value()))
                    .filter(m -> m != null)
                    .toList();

            if (!mappings.isEmpty()) {
                apiKeyCache.replaceAll(mappings);
                log.info("Loaded {} API keys from etcd on startup", mappings.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load initial API keys from etcd: {}. "
                    + "Scheduled poll will populate ApiKeyCache.",
                    e.getMessage());
        }
    }

    // ── Watch loop ──────────────────────────────────────────────────────

    private void startWatch() {
        try {
            watcher = watchClient.watch(prefix, this::onWatchEvent);
            cacheRefresher.onWatchConnected();
            log.info("etcd API key watch established on prefix={}", prefix);
        } catch (Exception e) {
            log.warn("Failed to start etcd API key watch: {}. Retrying in {}ms.",
                    e.getMessage(), watchRetryDelayMs);
            scheduleRetry();
        }
    }

    private void onWatchEvent(WatchEvent event) {
        String key = event.getKeyValue().getKey().toString(StandardCharsets.UTF_8);
        log.debug("etcd api key watch event: type={}, key={}", event.getEventType(), key);

        try {
            switch (event.getEventType()) {
                case PUT -> {
                    TenantApiKeyMapping mapping = deserialize(
                            event.getKeyValue().getValue().toString(StandardCharsets.UTF_8));
                    if (mapping != null && mapping.getApiKeyHash() != null
                            && mapping.getTenantId() != null) {
                        apiKeyCache.put(mapping.getApiKeyHash(), mapping.getTenantId());
                    }
                }
                case DELETE -> {
                    String hash = extractHash(key);
                    if (hash != null) {
                        apiKeyCache.remove(hash);
                    }
                }
                default -> log.debug("Unhandled etcd watch event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.warn("Error handling etcd api key watch event for key={}: {}", key, e.getMessage());
        }
    }

    private void scheduleRetry() {
        cacheRefresher.onWatchDisconnected();
        retryExecutor.schedule(() -> {
            log.info("Retrying etcd API key watch...");
            startWatch();
        }, watchRetryDelayMs, TimeUnit.MILLISECONDS);
    }

    private TenantApiKeyMapping deserialize(String json) {
        try {
            return objectMapper.readValue(json, TenantApiKeyMapping.class);
        } catch (IOException e) {
            log.warn("Failed to deserialize TenantApiKeyMapping: {}", e.getMessage());
            return null;
        }
    }

    private String extractHash(String key) {
        try {
            String[] parts = key.split("/");
            return parts[parts.length - 1];
        } catch (Exception e) {
            log.warn("Failed to extract API key hash from etcd key: {}", key);
            return null;
        }
    }
}
