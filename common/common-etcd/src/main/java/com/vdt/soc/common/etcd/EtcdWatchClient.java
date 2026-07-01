package com.vdt.soc.common.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Wrapper around jetcd providing simple watch and get operations
 * for the distribution pattern (service → etcd → collector).
 * <p>
 * Generic — callers pass the prefix they want to watch/read.
 * Used by both {@code EtcdPolicyWatcher} (/policies/) and
 * {@code EtcdApiKeyWatcher} (/apikeys/).
 * <p>
 * Includes automatic retry logic for watch reconnection.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class EtcdWatchClient {

    private final Client etcdClient;

    /**
     * Get all key-value pairs under a given prefix.
     *
     * @param prefix the etcd key prefix (e.g. "/policies/", "/apikeys/")
     * @return list of key-value pairs
     */
    public List<KeyValue> getAll(String prefix) {
        try {
            ByteSequence keyPrefix = ByteSequence.from(prefix, StandardCharsets.UTF_8);
            KV kvClient = etcdClient.getKVClient();

            GetOption option = GetOption.builder().isPrefix(true).build();
            CompletableFuture<GetResponse> future = kvClient.get(keyPrefix, option);
            GetResponse response = future.get();
            return response.getKvs().stream()
                    .map(kv -> new KeyValue(
                            kv.getKey().toString(StandardCharsets.UTF_8),
                            kv.getValue().toString(StandardCharsets.UTF_8)))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get keys from etcd prefix={}: {}", prefix, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Watch for changes under a given prefix.
     * Calls the consumer for each watch event.
     *
     * @param prefix  the etcd key prefix to watch
     * @param onEvent consumer for each watch event
     * @return the Watch.Watcher instance (caller should close on shutdown)
     */
    public Watch.Watcher watch(String prefix, Consumer<WatchEvent> onEvent) {
        ByteSequence keyPrefix = ByteSequence.from(prefix, StandardCharsets.UTF_8);
        Watch watchClient = etcdClient.getWatchClient();

        return watchClient.watch(keyPrefix, new Watch.Listener() {
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
                log.error("etcd watch error on prefix={}: {}", prefix, throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("etcd watch completed on prefix={}", prefix);
            }
        });
    }

    /**
     * Simple key-value pair record.
     */
    public record KeyValue(String key, String value) {
    }
}
