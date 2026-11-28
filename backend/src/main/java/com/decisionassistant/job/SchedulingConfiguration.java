package com.decisionassistant.job;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** job mode에서는 어떤 {@code @Scheduled} 작업도 등록하지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Conditional(JobMode.Inactive.class)
class SchedulingConfiguration {
}
