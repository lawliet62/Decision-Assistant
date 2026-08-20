package com.decisionassistant.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("decision-assistant.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String model,
        Duration timeout
) {
}
