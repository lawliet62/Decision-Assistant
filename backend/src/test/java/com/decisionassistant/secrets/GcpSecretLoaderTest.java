package com.decisionassistant.secrets;

import com.google.api.gax.rpc.StatusCode;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.*;

class GcpSecretLoaderTest {
    @Test void boundsCredentialDiscoveryAsWellAsRpcTime() {
        assertThatThrownBy(() -> GcpSecretLoader.withinDeadline(() -> {
            new CountDownLatch(1).await();
            return "never";
        }, Duration.ofMillis(25))).hasMessageContaining("TIMEOUT").hasNoCause();
    }

    @Test void errorsDoNotRetainSensitiveSdkMessages() {
        assertThatThrownBy(() -> GcpSecretLoader.withinDeadline(() -> {
            throw new IOException("sensitive-fixture");
        }, Duration.ofSeconds(1))).hasMessageContaining("AUTHENTICATION_OR_CLIENT_UNAVAILABLE")
                .hasMessageNotContaining("sensitive-fixture").hasNoCause();
    }

    @Test void returnsValuesOnlyOnSuccess() {
        assertThat(GcpSecretLoader.withinDeadline(() -> "fake-value", Duration.ofSeconds(1))).isEqualTo("fake-value");
    }

    @Test void retriesOnlyTransientFailuresAtMostOnce() throws Exception {
        var call = GcpSecretLoader.settings().accessSecretVersionSettings();
        assertThat(call.getRetryableCodes()).containsExactlyInAnyOrder(StatusCode.Code.UNAVAILABLE, StatusCode.Code.DEADLINE_EXCEEDED);
        assertThat(call.getRetrySettings().getMaxAttempts()).isEqualTo(2);
        assertThat(call.getRetrySettings().getMaxRpcTimeoutDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test void developerCredentialsCannotBeUsedAsProductionIdentity() {
        var user = UserCredentials.newBuilder().setClientId("fake-id").setClientSecret("fake-secret")
                .setRefreshToken("fake-refresh").build();
        assertThatCode(() -> GcpSecretLoader.validateIdentity(user, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> GcpSecretLoader.validateIdentity(user, false)).hasMessageContaining("IDENTITY_TYPE_NOT_ALLOWED");
        assertThatCode(() -> GcpSecretLoader.validateIdentity(ComputeEngineCredentials.create(), false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> GcpSecretLoader.validateIdentity(GoogleCredentials.newBuilder().build(), true))
                .hasMessageContaining("IDENTITY_TYPE_NOT_ALLOWED");
    }
}
