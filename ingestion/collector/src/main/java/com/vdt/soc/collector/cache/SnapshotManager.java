package com.vdt.soc.collector.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.soc.collector.config.CacheProperties;
import com.vdt.soc.common.model.dto.PolicyDTO;
import com.vdt.soc.common.model.dto.TenantApiKeyMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Manages JSON snapshot files for cache fallback.
 *
 * Snapshot files are written on every successful cache refresh
 * and read when internal API calls fail (license-service or tenant-service down).
 *
 * All I/O is blocking (Files.read and Files.write) but called exclusively
 * from scheduled methods, never from Netty event loop threads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotManager {

    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<PolicyDTO>> POLICIES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<TenantApiKeyMapping>> API_KEYS_TYPE = new TypeReference<>() {};

    /**
     * Write policy cache snapshot to disk.
     *
     * @param policies current policy cache contents
     */
    public void writePolicies(List<PolicyDTO> policies) {
        try {
            Path dir = ensureDir();
            Path file = dir.resolve(cacheProperties.getPolicySnapshotFile());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), policies);
            log.debug("Policy snapshot written: {} entries → {}", policies.size(), file);
        } catch (IOException e) {
            log.warn("Failed to write policy snapshot: {}", e.getMessage());
        }
    }

    /**
     * Write API key cache snapshot to disk.
     *
     * @param mappings current API key cache contents
     */
    public void writeApiKeys(List<TenantApiKeyMapping> mappings) {
        try {
            Path dir = ensureDir();
            Path file = dir.resolve(cacheProperties.getApiKeySnapshotFile());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), mappings);
            log.debug("API key snapshot written: {} entries → {}", mappings.size(), file);
        } catch (IOException e) {
            log.warn("Failed to write API key snapshot: {}", e.getMessage());
        }
    }

    /**
     * Read policy cache snapshot from disk (fallback).
     *
     * @return list of policies, or empty list if snapshot unavailable
     */
    public List<PolicyDTO> readPolicies() {
        try {
            Path file = resolvePath(cacheProperties.getPolicySnapshotFile());
            if (!Files.exists(file)) {
                log.warn("Policy snapshot file not found: {}", file);
                return Collections.emptyList();
            }
            List<PolicyDTO> policies = objectMapper.readValue(file.toFile(), POLICIES_TYPE);
            log.info("Policy snapshot loaded: {} entries from {}", policies.size(), file);
            return policies;
        } catch (IOException e) {
            log.warn("Failed to read policy snapshot: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Read API key cache snapshot from disk (fallback).
     *
     * @return list of API key mappings, or empty list if snapshot unavailable
     */
    public List<TenantApiKeyMapping> readApiKeys() {
        try {
            Path file = resolvePath(cacheProperties.getApiKeySnapshotFile());
            if (!Files.exists(file)) {
                log.warn("API key snapshot file not found: {}", file);
                return Collections.emptyList();
            }
            List<TenantApiKeyMapping> mappings = objectMapper.readValue(file.toFile(), API_KEYS_TYPE);
            log.info("API key snapshot loaded: {} entries from {}", mappings.size(), file);
            return mappings;
        } catch (IOException e) {
            log.warn("Failed to read API key snapshot: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Path ensureDir() throws IOException {
        Path dir = Path.of(cacheProperties.getSnapshotDir());
        Files.createDirectories(dir);
        return dir;
    }

    private Path resolvePath(String filename) {
        return Path.of(cacheProperties.getSnapshotDir(), filename);
    }
}
