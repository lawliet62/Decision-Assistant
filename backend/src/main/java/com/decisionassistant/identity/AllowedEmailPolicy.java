package com.decisionassistant.identity;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AllowedEmailPolicy {

    private final Set<String> allowed;

    public AllowedEmailPolicy(AuthProperties properties, Environment environment) {
        this.allowed = properties.allowedEmails().stream()
                .map(AllowedEmailPolicy::normalize)
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (!environment.matchesProfiles("local", "test") && allowed.isEmpty()) {
            throw new IllegalStateException("DECISION_ASSISTANT_ALLOWED_EMAILS must contain at least one email");
        }
    }

    public boolean allows(String email) {
        return allowed.contains(normalize(email));
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
