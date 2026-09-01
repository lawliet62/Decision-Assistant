package com.decisionassistant.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("decision-assistant.reminders")
public record ReminderProperties(boolean enabled, String resendBaseUrl, String resendApiKey, String fromAddress) {
    public boolean configured() { return enabled && resendApiKey != null && !resendApiKey.isBlank() && !"disabled".equals(resendApiKey); }
}
