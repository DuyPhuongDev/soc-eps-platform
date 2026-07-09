package com.vdt.soc.collector.engine;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricEngine {

    private final Counter totalEventsCounter;

    public MetricEngine(MeterRegistry meterRegistry) {
        this.totalEventsCounter = Counter.builder("soc_collector_events_total")
                .description("Total event")
                .register(meterRegistry);
    }

    /**
     * Tăng bộ đếm cho Single Event (count = 1)
     */
    public void increment() {
        this.totalEventsCounter.increment();
    }

    /**
     * Tăng bộ đếm cho một mẻ Batch (cộng dồn số lượng size)
     */
    public void increment(int count) {
        if (count > 0) {
            this.totalEventsCounter.increment(count);
        }
    }
}