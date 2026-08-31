package com.decisionassistant.identity;

import java.util.UUID;

public record CurrentUser(UUID id, String email, String displayName) {
}
