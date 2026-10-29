package com.decisionassistant.guidance;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SpecificationPageFetcherTest {
    @Test void boundsTheWholeBodyAndRejectsOversizeOrRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        var bodyStarted = new CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/ok", exchange -> {
            var body = "사양".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) { out.write(new byte[17]); }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write('x'); out.flush();
                bodyStarted.countDown();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        server.start();
        try {
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(SpecificationPageFetcher.fetch(URI.create(base + "/ok"), Duration.ofSeconds(3), 16)).isEqualTo("사양");
            assertThatThrownBy(() -> SpecificationPageFetcher.fetch(URI.create(base + "/large"), Duration.ofSeconds(3), 16))
                    .hasRootCauseMessage("SOURCE_TOO_LARGE");
            assertThatThrownBy(() -> SpecificationPageFetcher.fetch(URI.create(base + "/redirect"), Duration.ofSeconds(3), 16))
                    .hasRootCauseMessage("SOURCE_UNAVAILABLE");
            assertTimeoutPreemptively(Duration.ofSeconds(4), () -> assertThatThrownBy(
                    () -> SpecificationPageFetcher.fetch(URI.create(base + "/slow"), Duration.ofSeconds(1), 16))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class));
            assertThat(bodyStarted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); server.stop(0); executor.close(); }
    }
}
