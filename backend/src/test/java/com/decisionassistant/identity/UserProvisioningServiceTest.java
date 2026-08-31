package com.decisionassistant.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock ApplicationUserRepository users;
    @Mock ExternalIdentityRepository identities;
    @Mock AllowedEmailPolicy allowedEmails;
    @Mock OidcUser oidcUser;

    UserProvisioningService service;

    @BeforeEach
    void setUp() throws MalformedURLException {
        service = new UserProvisioningService(users, identities, allowedEmails);
        when(oidcUser.getEmail()).thenReturn("Person@Example.com");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        lenient().when(oidcUser.getIssuer()).thenReturn(new URL("https://tenant.example.com/"));
        lenient().when(oidcUser.getSubject()).thenReturn("auth0|subject");
        lenient().when(oidcUser.getFullName()).thenReturn("Person");
    }

    @Test
    void provisionsAnAllowedVerifiedIdentity() {
        when(allowedEmails.allows("person@example.com")).thenReturn(true);
        when(identities.findByIssuerAndSubject(any(), any())).thenReturn(Optional.empty());
        when(users.findByEmailNormalized("person@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.provision(oidcUser);

        assertThat(result.emailNormalized()).isEqualTo("person@example.com");
        verify(identities).saveAndFlush(any(ExternalIdentity.class));
    }

    @Test
    void rejectsAnEmailOutsideTheAllowlist() {
        when(allowedEmails.allows("person@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.provision(oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("verified email");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnUnverifiedEmail() {
        when(oidcUser.getEmailVerified()).thenReturn(false);

        assertThatThrownBy(() -> service.provision(oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("verified email");
        verify(allowedEmails, never()).allows(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refreshesTheUserAndIdentityForAnExistingLogin() {
        var userId = UUID.randomUUID();
        var existingUser = new ApplicationUser(
                userId, "old@example.com", "old@example.com", "Old Name", Instant.EPOCH);
        var existingIdentity = new ExternalIdentity(
                userId, "https://tenant.example.com/", "auth0|subject", Instant.EPOCH);
        when(allowedEmails.allows("person@example.com")).thenReturn(true);
        when(identities.findByIssuerAndSubject(
                "https://tenant.example.com/", "auth0|subject")).thenReturn(Optional.of(existingIdentity));
        when(users.findById(userId)).thenReturn(Optional.of(existingUser));
        when(users.findByEmailNormalized("person@example.com")).thenReturn(Optional.empty());

        var result = service.provision(oidcUser);

        assertThat(result).isSameAs(existingUser);
        assertThat(result.email()).isEqualTo("Person@Example.com");
        assertThat(result.emailNormalized()).isEqualTo("person@example.com");
        assertThat(result.displayName()).isEqualTo("Person");
        assertThat(result.lastLoginAt()).isAfter(Instant.EPOCH);
        assertThat(existingIdentity.lastLoginAt()).isAfter(Instant.EPOCH);
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnExistingIdentityWhenItsEmailBelongsToAnotherUser() {
        var userId = UUID.randomUUID();
        var existingUser = new ApplicationUser(
                userId, "old@example.com", "old@example.com", "Old Name", Instant.EPOCH);
        var existingIdentity = new ExternalIdentity(
                userId, "https://tenant.example.com/", "auth0|subject", Instant.EPOCH);
        var otherUser = new ApplicationUser(
                UUID.randomUUID(), "person@example.com", "person@example.com", "Other", Instant.EPOCH);
        when(allowedEmails.allows("person@example.com")).thenReturn(true);
        when(identities.findByIssuerAndSubject(
                "https://tenant.example.com/", "auth0|subject")).thenReturn(Optional.of(existingIdentity));
        when(users.findById(userId)).thenReturn(Optional.of(existingUser));
        when(users.findByEmailNormalized("person@example.com")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.provision(oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("다른 로그인 방식");
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAutomaticLinkingForTheSameEmailAndDifferentSubject() {
        var existing = new ApplicationUser(
                UUID.randomUUID(), "person@example.com", "person@example.com", "Existing", Instant.EPOCH);
        when(allowedEmails.allows("person@example.com")).thenReturn(true);
        when(identities.findByIssuerAndSubject(any(), any())).thenReturn(Optional.empty());
        when(users.findByEmailNormalized("person@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.provision(oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("처음 사용한 로그인 방식");
        verify(identities, never()).saveAndFlush(any());
    }
}
