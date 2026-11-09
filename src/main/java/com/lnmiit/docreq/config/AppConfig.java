package com.lnmiit.docreq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected everywhere time is needed, so tests can control it. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Scheduled jobs are switched off in tests, which call the jobs directly. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
