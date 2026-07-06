package com.vdt.soc.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration: dedicated thread pool for alert-mail sends
 * so that SMTP latency does not block the Kafka consumer thread.
 */
@Configuration
@EnableAsync
public class MailConfig {

    @Bean("alertMailExecutor")
    public Executor alertMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("alert-mail-");
        executor.initialize();
        return executor;
    }
}
