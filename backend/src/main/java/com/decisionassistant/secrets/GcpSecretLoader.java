package com.decisionassistant.secrets;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.ExternalAccountCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class GcpSecretLoader {
    private GcpSecretLoader() {}

    static Map<String, String> load(Map<String, String> resources, boolean local) {
        // Also bounds ADC discovery/token acquisition; SDK RPC deadlines alone do not cover bootstrap.
        return withinDeadline(() -> {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            validateIdentity(credentials, local);
            credentials = credentials.createScoped(SecretManagerServiceSettings.getDefaultServiceScopes());
            var settings = settings().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            try (var client = SecretManagerServiceClient.create(settings)) {
                var result = new LinkedHashMap<String, String>();
                for (var resource : resources.entrySet()) {
                    var payload = client.accessSecretVersion(resource.getValue()).getPayload();
                    result.put(resource.getKey(), payload.getData().toStringUtf8());
                }
                return result;
            }
        }, Duration.ofSeconds(30));
    }

    static void validateIdentity(GoogleCredentials credentials, boolean local) {
        if (local && credentials instanceof ImpersonatedCredentials impersonated) {
            credentials = impersonated.getSourceCredentials();
        }
        boolean permitted = local
                ? credentials instanceof UserCredentials
                : credentials instanceof ComputeEngineCredentials || credentials instanceof ExternalAccountCredentials;
        if (!permitted) throw new SecretBootstrapException("IDENTITY_TYPE_NOT_ALLOWED");
    }

    static SecretManagerServiceSettings.Builder settings() throws java.io.IOException {
        var settings = SecretManagerServiceSettings.newBuilder();
        var call = settings.accessSecretVersionSettings();
        call.setRetryableCodes(StatusCode.Code.UNAVAILABLE, StatusCode.Code.DEADLINE_EXCEEDED);
        call.setRetrySettings(call.getRetrySettings().toBuilder()
                .setInitialRetryDelayDuration(Duration.ofMillis(250))
                .setMaxRetryDelayDuration(Duration.ofMillis(250))
                .setRetryDelayMultiplier(1)
                .setInitialRpcTimeoutDuration(Duration.ofSeconds(5))
                .setMaxRpcTimeoutDuration(Duration.ofSeconds(5))
                .setRpcTimeoutMultiplier(1)
                .setTotalTimeoutDuration(Duration.ofSeconds(11))
                .setMaxAttempts(2).build());
        return settings;
    }

    static <T> T withinDeadline(Callable<T> work, Duration timeout) {
        var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("secret-bootstrap").factory());
        var future = executor.submit(work);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            throw new SecretBootstrapException("TIMEOUT");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new SecretBootstrapException("INTERRUPTED");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof SecretBootstrapException safe) throw safe;
            if (failure.getCause() instanceof ApiException api) {
                throw new SecretBootstrapException("GCP_" + api.getStatusCode().getCode().name());
            }
            throw new SecretBootstrapException("AUTHENTICATION_OR_CLIENT_UNAVAILABLE");
        } finally {
            future.cancel(true);
            executor.shutdownNow();
        }
    }
}
