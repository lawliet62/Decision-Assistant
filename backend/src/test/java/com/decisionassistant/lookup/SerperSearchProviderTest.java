package com.decisionassistant.lookup;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class SerperSearchProviderTest {
    @Test void sendsOnlyQueryAndReturnsOrganicResults() throws Exception {
        var body = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-API-KEY"));
            var bytes = """
                    {"organic":[{"title":"Buds","link":"https://example.com/buds","snippet":"Product page"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var adapter = adapter(server, Duration.ofSeconds(2));
            assertThat(adapter.search("Buds", 5)).singleElement().satisfies(item -> {
                assertThat(item.title()).isEqualTo("Buds");
                assertThat(item.url()).isEqualTo("https://example.com/buds");
            });
            assertThat(body.get()).contains("\"q\":\"Buds\"", "\"num\":5").doesNotContain("intent", "owner");
            assertThat(key.get()).isEqualTo("test-secret");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {301, 401, 429, 500})
    void upstreamFailureIsSanitizedAndNotRetried(int status) throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            var bytes = "test-secret and raw query".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> adapter(server, Duration.ofSeconds(2)).search("Buds", 5))
                    .isInstanceOf(LookupException.class).hasMessageNotContaining("test-secret")
                    .hasMessageNotContaining("raw query");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void timeoutIsBoundedAndDoesNotRetry() throws Exception {
        var calls = new AtomicInteger();
        var releaseResponse = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            try { releaseResponse.await(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> adapter(server, Duration.ofSeconds(2)).search("Buds", 5))
                    .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.code()).isEqualTo("SEARCH_TIMEOUT"));
            assertThat(calls.get()).isEqualTo(1);
        } finally { releaseResponse.countDown(); server.stop(0); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"not json", "{}"})
    void malformedPayloadFailsWithoutExposingBody(String body) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> adapter(server, Duration.ofSeconds(2)).search("Buds", 5))
                    .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.code()).isEqualTo("SEARCH_PROVIDER_UNAVAILABLE"));
        } finally { server.stop(0); }
    }

    private SerperSearchProvider adapter(HttpServer server, Duration timeout) {
        return new SerperSearchProvider(new SearchProperties(true, "test-secret", 1000),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"), timeout);
    }
}
