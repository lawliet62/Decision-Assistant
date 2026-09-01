package com.decisionassistant.guidance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("decision-assistant.web")
public record WebProperties(boolean enabled, String baseUrl, String apiKey, Duration timeout) {
    public boolean configured() { return enabled && apiKey != null && !apiKey.isBlank() && !"disabled".equals(apiKey); }
}
