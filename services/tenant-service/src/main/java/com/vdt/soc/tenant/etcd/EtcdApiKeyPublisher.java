package com.vdt.soc.tenant.etcd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.TenantApiKeyMapping;
import com.vdt.soc.common.etcd.EtcdProperties;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.PutResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes active API key mappings to etcd so collector instances
 * receive real-time updates via etcd watch.
 * <p>
 * Key pattern: /apikeys/{sha256Hash} → {"tenantId": "...", "apiKeyHash": "..."}
 * <p>
 * Operations:
 * <ul>
 *   <li>{@link #publish(UUID, String)} — create/update an API key entry</li>
 *   <li>{@link #remove(String)} — delete an API key entry (on revoke)</li>
 *   <li>{@link #publishAll(List)} — bulk publish all active API keys (startup sync)</li>
 * </ul>
 * <p>
 * All etcd calls are blocking. Called from transactional service methods —
 * blocking is acceptable because these run in the transaction thread pool.
 * <p>
 * Failures are logged but do NOT roll back the DB transaction —
 * the internal /internal/api-keys endpoint serves as a fallback for
 * the collector's scheduled poll.
 */
@Slf4j
@Component
public class EtcdApiKeyPublisher {

    private static final long PUBLISH_TIMEOUT_MS = 3000;
    private final Client etcdClient;
    private final EtcdProperties properties;
    private final ObjectMapper objectMapper;

    public EtcdApiKeyPublisher(Client etcdClient,
                               EtcdProperties properties,
                               ObjectMapper objectMapper) {
        this.etcdClient = etcdClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish (create or update) a single API key mapping to etcd.
     * Key: /apikeys/{apiKeyHash}
     *
     * @param tenantId   the tenant this API key belongs to
     * @param apiKeyHash SHA-256 hex of the raw API key
     */
    public void publish(UUID tenantId, String apiKeyHash) {
        String key = properties.getKeyPrefix() + apiKeyHash;
        String value;
        try {
            value = objectMapper.writeValueAsString(
                    TenantApiKeyMapping.builder()
                            .tenantId(tenantId)
                            .apiKeyHash(apiKeyHash)
                            .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TenantApiKeyMapping for hash {}: {}",
                    apiKeyHash, e.getMessage());
            return;
        }

        ByteSequence keyBs = ByteSequence.from(key, StandardCharsets.UTF_8);
        ByteSequence valueBs = ByteSequence.from(value, StandardCharsets.UTF_8);
        KV kvClient = etcdClient.getKVClient();

        try {
            CompletableFuture<PutResponse> future = kvClient.put(keyBs, valueBs);
            future.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.info("API key published to etcd: key={}, tenant={}", key, tenantId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("API key publish interrupted for hash {}: {}", apiKeyHash, e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to publish API key to etcd for hash {}: {}. "
                            + "Collector will pick up via poll fallback.",
                    apiKeyHash, e.getMessage());
        }
    }

    /**
     * Remove an API key mapping from etcd.
     *
     * @param apiKeyHash SHA-256 hex of the revoked API key
     */
    public void remove(String apiKeyHash) {
        String key = properties.getKeyPrefix() + apiKeyHash;
        ByteSequence keyBs = ByteSequence.from(key, StandardCharsets.UTF_8);
        KV kvClient = etcdClient.getKVClient();

        try {
            CompletableFuture<DeleteResponse> future = kvClient.delete(keyBs);
            DeleteResponse response = future.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long deleted = response.getDeleted();
            log.info("API key removed from etcd: key={}, deleted={}", key, deleted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("API key remove interrupted for hash {}: {}", apiKeyHash, e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to remove API key from etcd for hash {}: {}",
                    apiKeyHash, e.getMessage());
        }
    }

    /**
     * Bulk-publish all active API key mappings (e.g. on startup).
     * Each key is published individually to keep the publish path simple.
     *
     * @param mappings list of {tenantId, apiKeyHash} pairs
     */
    public void publishAll(List<TenantApiKeyMapping> mappings) {
        log.info("Bulk publishing {} API keys to etcd...", mappings.size());
        int success = 0;
        for (TenantApiKeyMapping m : mappings) {
            if (m.getApiKeyHash() != null && m.getTenantId() != null) {
                publish(m.getTenantId(), m.getApiKeyHash());
                success++;
            }
        }
        log.info("API key bulk publish done: {}/{} successful", success, mappings.size());
    }
}
