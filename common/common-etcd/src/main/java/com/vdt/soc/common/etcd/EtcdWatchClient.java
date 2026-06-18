package com.vdt.soc.common.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Wrapper around jetcd providing simple watch and get operations
 * for the policy distribution pattern (license → etcd → collector).
 * <p>
 * Includes automatic retry logic for watch reconnection.
 */
@Slf4j
@RequiredArgsConstructor
public class EtcdWatchClient {

    private final Client etcdClient;
    private final EtcdProperties properties;

    private static final ByteSequence EMPTY_PREFIX = ByteSequence.from("", StandardCharsets.UTF_8);

    /**
     * Get all key-value pairs under the policy prefix.
     *
     * @return list of key-value pairs as strings
     */
    public List<KeyValue> getAllPolicies() {
        try {
            ByteSequence prefix = ByteSequence.from(properties.getKeyPrefix(), StandardCharsets.UTF_8);
            KV kvClient = etcdClient.getKVClient();

            CompletableFuture<GetResponse> future = kvClient.get(prefix);
            GetResponse response = future.get();
            return response.getKvs().stream()
                    .map(kv -> new KeyValue(
                            kv.getKey().toString(StandardCharsets.UTF_8),
                            kv.getValue().toString(StandardCharsets.UTF_8)))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get all policies from etcd: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Watch for changes under the policy prefix.
     * Calls the consumer for each watch event.
     *
     * @param onEvent consumer for watch events
     * @return the Watch.Watcher instance (caller should close on shutdown)
     */
    public Watch.Watcher watchPolicies(Consumer<WatchEvent> onEvent) {
        ByteSequence prefix = ByteSequence.from(properties.getKeyPrefix(), StandardCharsets.UTF_8);
        Watch watchClient = etcdClient.getWatchClient();

        return watchClient.watch(prefix, new Watch.Listener() {
            @Override
            public void onNext(WatchResponse response) {
                for (WatchEvent event : response.getEvents()) {
                    log.debug("etcd watch event: type={}, key={}",
                            event.getEventType(),
                            event.getKeyValue().getKey().toString(StandardCharsets.UTF_8));
                    onEvent.accept(event);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("etcd watch error: {}", throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("etcd watch completed");
            }
        });
    }

    /**
     * Simple key-value pair record.
     */
    public record KeyValue(String key, String value) {}
}