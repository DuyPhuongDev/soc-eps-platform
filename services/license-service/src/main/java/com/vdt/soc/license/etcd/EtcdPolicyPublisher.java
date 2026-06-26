package com.vdt.soc.license.etcd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.common.core.dto.PolicyDTO;
import com.vdt.soc.common.etcd.EtcdProperties;
import com.vdt.soc.license.entity.License;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.PutResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes active license policies to etcd so collector instances
 * receive real-time updates via etcd watch.
 * <p>
 * Key pattern: /policies/{tenantId}
 * Value:       PolicyDTO serialized as JSON
 * <p>
 * Operations:
 * <ul>
 *   <li>{@link #publish(License)} — create or update a policy entry</li>
 *   <li>{@link #remove(UUID)} — delete a policy entry (on revoke)</li>
 * </ul>
 * <p>
 * All etcd calls are blocking (CompletableFuture.get with timeout).
 * Called from transactional service methods — blocking is acceptable here
 * because the calls run in the transaction thread pool, not Netty event loop.
 * <p>
 * Failures are logged but do NOT roll back the DB transaction —
 * the internal /internal/policies endpoint serves as a fallback for
 * the collector's scheduled poll.
 */
@Slf4j
@Component
public class EtcdPolicyPublisher {

    private static final long PUBLISH_TIMEOUT_MS = 3000;
    private final Client etcdClient;
    private final EtcdProperties properties;
    private final ObjectMapper objectMapper;
    private final ByteSequence prefix;

    public EtcdPolicyPublisher(Client etcdClient,
                               EtcdProperties properties,
                               ObjectMapper objectMapper) {
        this.etcdClient = etcdClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.prefix = ByteSequence.from(properties.getKeyPrefix(), StandardCharsets.UTF_8);
    }

    /**
     * Publish (create or update) a policy for the given license.
     * Key: /policies/{tenantId}
     * No lease/TTL — policy is explicitly removed on revoke.
     *
     * @param license the license entity to publish
     */
    public void publish(License license) {
        if (!license.isActive()) {
            log.debug("Skipping etcd publish for inactive license: id={}", license.getId());
            return;
        }

        PolicyDTO policy = toPolicyDTO(license);
        String key = properties.getKeyPrefix() + license.getTenantId();
        String value;
        try {
            value = objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PolicyDTO for tenant {}: {}",
                    license.getTenantId(), e.getMessage());
            return;
        }

        ByteSequence keyBs = ByteSequence.from(key, StandardCharsets.UTF_8);
        ByteSequence valueBs = ByteSequence.from(value, StandardCharsets.UTF_8);
        KV kvClient = etcdClient.getKVClient();

        try {
            CompletableFuture<PutResponse> future = kvClient.put(keyBs, valueBs);
            PutResponse response = future.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.info("Policy published to etcd: key={}, tenant={}, plan={}, eps={}",
                    key, license.getTenantId(), license.getPlan(), license.getEpsQuota());
            log.debug("etcd put response: {}", response.getHeader());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Policy publish interrupted for tenant {}: {}", license.getTenantId(), e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to publish policy to etcd for tenant {}: {}. "
                    + "Collector will pick up via poll fallback.",
                    license.getTenantId(), e.getMessage());
        }
    }

    /**
     * Remove a policy from etcd (e.g. on license revoke or delete).
     *
     * @param tenantId the tenant whose policy should be removed
     */
    public void remove(UUID tenantId) {
        String key = properties.getKeyPrefix() + tenantId;
        ByteSequence keyBs = ByteSequence.from(key, StandardCharsets.UTF_8);
        KV kvClient = etcdClient.getKVClient();

        try {
            CompletableFuture<DeleteResponse> future = kvClient.delete(keyBs);
            DeleteResponse response = future.get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long deleted = response.getDeleted();
            log.info("Policy removed from etcd: key={}, deleted={}", key, deleted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Policy remove interrupted for tenant {}: {}", tenantId, e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to remove policy from etcd for tenant {}: {}",
                    tenantId, e.getMessage());
        }
    }

    private PolicyDTO toPolicyDTO(License license) {
        return PolicyDTO.builder()
                .tenantId(license.getTenantId())
                .plan(license.getPlan())
                .epsQuota(license.getEpsQuota())
                .mode(license.getMode())
                .burstMultiplier(license.getBurstMultiplier())
                .validUntil(license.getEndDate())
                .build();
    }
}
