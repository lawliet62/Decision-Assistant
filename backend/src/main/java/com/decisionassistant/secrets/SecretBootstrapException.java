package com.decisionassistant.secrets;

/** Deliberately does not retain SDK/config exceptions: they may contain credentials. */
final class SecretBootstrapException extends IllegalStateException {
    SecretBootstrapException(String code) {
        super("Secret bootstrap failed: " + code + ". See docs/deployment.md (Secret management).");
    }
}
