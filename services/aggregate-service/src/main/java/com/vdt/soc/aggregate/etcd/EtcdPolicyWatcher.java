package com.vdt.soc.aggregate.etcd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.aggregate.cache.PolicyCache;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.etcd.EtcdWatchClient;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.watch.WatchEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EtcdPolicyWatcher {

    private final EtcdWatchClient watchClient;
    private final PolicyCache policyCache;
    private final ObjectMapper objectMapper;
    private final String prefix;
    private final long watchRetryDelayMs;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("etcd-policy-watch-retry-").factory());
    private volatile Watch.Watcher watcher;

    public EtcdPolicyWatcher(EtcdWatchClient watchClient,
                             PolicyCache policyCache,
                             ObjectMapper objectMapper,
                             @Value("${app.etcd.policy-prefix:/policies/}") String prefix,
                             @Value("${app.etcd.watch-retry-delay-ms:3000}") long watchRetryDelayMs) {
        this.watchClient = watchClient;
        this.policyCache = policyCache;
        this.objectMapper = objectMapper;
        this.prefix = prefix;
        this.watchRetryDelayMs = watchRetryDelayMs;
    }

    @PostConstruct
    public void start() {
        log.info("Starting etcd policy watcher on prefix={}", prefix);
        loadInitial();
        startWatch();
    }

    @PreDestroy
    public void stop() {
        if (watcher != null) {
            watcher.close();
            log.info("etcd policy watcher closed");
        }
        retryExecutor.shutdownNow();
    }

    private void loadInitial() {
        try {
            List<EtcdWatchClient.KeyValue> kvs = watchClient.getAll(prefix);
            if (kvs.isEmpty()) {
                log.info("No policies found in etcd prefix={} — PolicyCache will be populated by scheduled poll", prefix);
                return;
            }
            List<PolicyDTO> policies = kvs.stream()
                    .map(kv -> deserialize(kv.value()))
                    .filter(Objects::nonNull)
                    .toList();
            if (!policies.isEmpty()) {
                policyCache.replaceAll(policies);
                log.info("Loaded {} policies from etcd on startup", policies.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load initial policies from etcd: {}", e.getMessage());
        }
    }

    private void startWatch() {
        try {
            watcher = watchClient.watch(prefix, this::onWatchEvent);
            log.info("etcd policy watch established on prefix={}", prefix);
        } catch (Exception e) {
            log.warn("Failed to start etcd policy watch: {}. Retrying in {}ms.", e.getMessage(), watchRetryDelayMs);
            scheduleRetry();
        }
    }

    private void onWatchEvent(WatchEvent event) {
        String key = event.getKeyValue().getKey().toString(StandardCharsets.UTF_8);
        log.debug("etcd policy watch event: type={}, key={}", event.getEventType(), key);
        try {
            switch (event.getEventType()) {
                case PUT -> {
                    PolicyDTO policy = deserialize(
                            event.getKeyValue().getValue().toString(StandardCharsets.UTF_8));
                    if (policy != null) {
                        policyCache.put(policy);
                    }
                }
                case DELETE -> {
                    UUID tenantId = extractTenantId(key);
                    if (tenantId != null) {
                        policyCache.remove(tenantId);
                    }
                }
                default -> log.debug("Unhandled etcd watch event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.warn("Error handling etcd policy watch event for key={}: {}", key, e.getMessage());
        }
    }

    private void scheduleRetry() {
        retryExecutor.schedule(() -> {
            log.info("Retrying etcd policy watch...");
            startWatch();
        }, watchRetryDelayMs, TimeUnit.MILLISECONDS);
    }

    private PolicyDTO deserialize(String json) {
        try {
            return objectMapper.readValue(json, PolicyDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize PolicyDTO: {}", e.getMessage());
            return null;
        }
    }

    private UUID extractTenantId(String key) {
        try {
            String[] parts = key.split("/");
            return UUID.fromString(parts[parts.length - 1]);
        } catch (Exception e) {
            log.warn("Failed to extract tenantId from etcd key: {}", key);
            return null;
        }
    }
}