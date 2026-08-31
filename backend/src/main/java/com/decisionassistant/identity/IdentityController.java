package com.decisionassistant.identity;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {

    private final CurrentUserProvider currentUser;

    public IdentityController(CurrentUserProvider currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@Parameter(hidden = true) CsrfToken csrfToken) {
        csrfToken.getToken();
        var user = currentUser.current();
        return new CurrentUserResponse(user.id(), user.email(), user.displayName());
    }

    public record CurrentUserResponse(UUID id, String email, String displayName) {
    }
}
