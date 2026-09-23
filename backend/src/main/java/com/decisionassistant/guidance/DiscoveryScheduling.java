package com.decisionassistant.guidance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
class DiscoveryScheduling {
    @Bean(name = "taskScheduler") ThreadPoolTaskScheduler taskScheduler() { return scheduler("workflow-"); }
    @Bean(name = "discoveryScheduler") ThreadPoolTaskScheduler discoveryScheduler() { return scheduler("discovery-"); }
    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
