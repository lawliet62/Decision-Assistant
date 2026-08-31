package com.decisionassistant.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("decision-assistant.auth")
public record AuthProperties(
        List<String> allowedEmails,
        String publicBaseUrl
) {
    public AuthProperties {
        allowedEmails = allowedEmails == null ? List.of() : List.copyOf(allowedEmails);
    }
}
