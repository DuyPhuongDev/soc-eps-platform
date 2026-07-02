package com.vdt.soc.collector.engine;

import com.vdt.soc.common.core.dto.PolicyDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;


public interface TokenBucketEngine {


    Mono<Long> tryConsume(UUID tenantId, PolicyDTO policy, long requested);


    default Mono<Boolean> tryConsume(UUID tenantId, PolicyDTO policy) {
        return tryConsume(tenantId, policy, 1L)
                .map(consumed -> consumed > 0);
    }
}
