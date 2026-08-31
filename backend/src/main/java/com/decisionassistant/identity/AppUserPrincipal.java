package com.decisionassistant.identity;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record AppUserPrincipal(UUID userId, String email, String displayName) implements Principal, Serializable {
    @Override
    public String getName() {
        return userId.toString();
    }
}
