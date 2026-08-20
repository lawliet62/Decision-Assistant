package com.decisionassistant.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AiCallExecutor {

    private final AiProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiCallExecutor(AiProperties properties) {
        this.properties = properties;
    }

    public <T> T call(Callable<T> callable) {
        var future = executor.submit(callable);
        try {
            return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AiInvocationException("AI provider timed out after " + properties.timeout(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiInvocationException("AI provider call was interrupted", exception);
        } catch (ExecutionException exception) {
            var cause = exception.getCause() == null ? exception : exception.getCause();
            throw new AiInvocationException("AI provider call failed: " + cause.getMessage(), cause);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
