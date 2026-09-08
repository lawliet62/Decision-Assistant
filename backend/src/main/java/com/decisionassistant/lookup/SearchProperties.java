package com.decisionassistant.lookup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("decision-assistant.search")
public record SearchProperties(boolean enabled, String apiKey, @Min(1) @Max(1000) int monthlyLimit) {
    public boolean configured() {
        return enabled && apiKey != null && !apiKey.isBlank() && !"disabled".equals(apiKey);
    }
}
