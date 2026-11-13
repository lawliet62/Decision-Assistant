package com.decisionassistant.guidance;

import com.decisionassistant.notification.ReminderProperties;
import com.decisionassistant.notification.ResendReminderDeliveryAdapter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** HTTP만 fixture로 대체하고 Boot의 실제 JSON converter와 provider adapter를 실행한다. */
class ProviderResponseConversionTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class));
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void firecrawlScrapeConvertsSuccessfulHttpJson() {
        respond("/scrape", 200, """
                {"success":true,"data":{"markdown":"Buds A ANC 지원","metadata":{"title":"제품 사양"}}}
                """);
        context.run(app -> {
            var provider = provider(app.getBean(RestClient.Builder.class));
            var page = provider.scrape("https://example.com/product");
            assertThat(page).isEqualTo(new WebEvidenceProvider.Page("제품 사양", "Buds A ANC 지원"));
        });
        assertThat(calls).hasValue(1);
    }

    @Test void firecrawlSearchConvertsSuccessfulHttpJson() {
        respond("/search", 200, """
                {"success":true,"data":{"web":[{"title":"Buds A","url":"https://example.com/a","description":"제품 설명"}]}}
                """);
        context.run(app -> assertThat(provider(app.getBean(RestClient.Builder.class)).search("이어폰", 3))
                .containsExactly(new WebSearchProvider.SearchResult("Buds A", "https://example.com/a", "제품 설명")));
        assertThat(calls).hasValue(1);
    }

    @Test void sonyUsesOnePostSelectionSnapshotAndNeverFallsBackToRawHtml() {
        var invalid = new AtomicInteger();
        var request = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/scrape", exchange -> {
            calls.incrementAndGet();
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var snapshot = java.util.Map.of("url", invalid.get() == 1 ? SonyC710nVerifier.URL + "0" : SonyC710nVerifier.URL,
                    "html", SonyC710nVerifierTest.HTML);
            var data = java.util.Map.of("markdown", "", "rawHtml", "before selection",
                    "metadata", java.util.Map.of("title", "WF-C710N", "statusCode", invalid.get() == 3 ? 500 : 200),
                    "actions", java.util.Map.of("scrapes", invalid.get() == 2 ? java.util.List.of() : java.util.List.of(snapshot)));
            var body = new tools.jackson.databind.ObjectMapper().writeValueAsBytes(java.util.Map.of("success", true, "data", data));
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
            exchange.close();
        });
        context.run(app -> {
            var provider = provider(app.getBean(RestClient.Builder.class));
            var page = provider.scrapeProduct(SonyC710nVerifier.URL);
            assertThat(page.html()).isEqualTo(SonyC710nVerifierTest.HTML);
            assertThat(page.resolvedUrl()).isEqualTo(SonyC710nVerifier.URL);
            var actions = new tools.jackson.databind.ObjectMapper().readTree(request.get()).path("actions");
            assertThat(actions.size()).isEqualTo(5);
            assertThat(actions.path(4).path("type").asText()).isEqualTo("scrape");
            for (int i = 1; i <= 3; i++) {
                invalid.set(i);
                assertThatThrownBy(() -> provider.scrapeProduct(SonyC710nVerifier.URL)).isInstanceOf(WebEvidenceException.class);
            }
        });
        assertThat(calls).hasValue(4);
    }

    @Test void samsungRequiresOnePostWaitSnapshotWithoutRawFallback() {
        var invalid=new AtomicInteger();var request=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/scrape",exchange->{
            calls.incrementAndGet();request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            var snapshot=java.util.Map.of("url",invalid.get()==1?SamsungBuds4Verifier.URL+"0":SamsungBuds4Verifier.URL,"html",SamsungBuds4VerifierTest.HTML);
            var data=java.util.Map.of("markdown","","rawHtml","initial page", "metadata",java.util.Map.of("statusCode",invalid.get()==3?403:200),"actions",java.util.Map.of("scrapes",invalid.get()==2?java.util.List.of():java.util.List.of(snapshot)));
            var bytes=new tools.jackson.databind.ObjectMapper().writeValueAsBytes(java.util.Map.of("success",true,"data",data));
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}exchange.close();
        });
        context.run(app->{
            var provider=provider(app.getBean(RestClient.Builder.class));
            assertThat(provider.scrapeProduct(SamsungBuds4Verifier.URL).html()).isEqualTo(SamsungBuds4VerifierTest.HTML);
            var actions=new tools.jackson.databind.ObjectMapper().readTree(request.get()).path("actions");
            assertThat(actions.size()).isEqualTo(2);assertThat(actions.path(0).path("milliseconds").asInt()).isEqualTo(8000);
            assertThat(actions.path(1).path("type").asText()).isEqualTo("scrape");
            for(int i=1;i<=3;i++){invalid.set(i);assertThatThrownBy(()->provider.scrapeProduct(SamsungBuds4Verifier.URL)).isInstanceOf(WebEvidenceException.class);}
        });
        assertThat(calls).hasValue(4);
    }

    @Test void productScrapeConvertsHtmlAndFinalUrlWithOneRequest() {
        var request = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/scrape", exchange -> {
            calls.incrementAndGet(); request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            // JSON serializer로 실제 HTML 문자열의 이스케이프를 구성한다.
            var body = new tools.jackson.databind.ObjectMapper().writeValueAsBytes(java.util.Map.of("success", true, "data", java.util.Map.of(
                    "markdown", "Buds ANC 지원", "rawHtml", "<script type='application/ld+json'>{\"@type\":\"Product\"}</script>",
                    "metadata", java.util.Map.of("title", "Buds", "url", "https://qcystore.co.kr/product/buds/1/"))));
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
            exchange.close();
        });
        context.run(app -> {
            var page = provider(app.getBean(RestClient.Builder.class)).scrapeProduct("https://qcystore.co.kr/product/buds/1/");
            assertThat(page.html()).contains("application/ld+json", "\"@type\":\"Product\"");
            assertThat(page.resolvedUrl()).isEqualTo("https://qcystore.co.kr/product/buds/1/");
        });
        assertThat(request.get()).contains("rawHtml", "markdown", "storeInCache");
        assertThat(new tools.jackson.databind.ObjectMapper().readTree(request.get()).path("maxAge").asInt(-1)).isZero();
        assertThat(new tools.jackson.databind.ObjectMapper().readTree(request.get()).has("actions")).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test void resendConvertsSuccessfulHttpJson() {
        respond("/emails", 200, "{\"id\":\"fixture-message-id\"}");
        context.run(app -> {
            var provider = new ResendReminderDeliveryAdapter(new ReminderProperties(true, baseUrl, "fixture", "test@example.com"),
                    app.getBean(RestClient.Builder.class));
            assertThat(provider.send(UUID.randomUUID(), "recipient@example.com", "비교", "https://example.com/decision"))
                    .isEqualTo("fixture-message-id");
        });
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"success\":\"true\"}", "{\"success\":true,\"data\":null}",
            "{\"success\":true,\"data\":{\"markdown\":42}}"})
    void rejectsInvalidScrapeShape(String json) {
        respond("/scrape", 200, json);
        context.run(app -> assertThatThrownBy(() -> provider(app.getBean(RestClient.Builder.class)).scrape("https://example.com/a"))
                .isInstanceOf(WebEvidenceException.class)
                .satisfies(error -> assertThat(((WebEvidenceException) error).reason()).isEqualTo(WebEvidenceException.Reason.RESPONSE_INVALID)));
        assertThat(calls).hasValue(1);
    }

    @Test void invalidJsonAndHttpRateLimitAreNotRetried() {
        respond("/scrape", 200, "not JSON");
        respond("/search", 429, "{\"error\":\"fixture-provider-secret\"}");
        context.run(app -> {
            var provider = provider(app.getBean(RestClient.Builder.class));
            assertThatThrownBy(() -> provider.scrape("https://example.com/a"))
                    .satisfies(error -> assertThat(DiscoveryFailure.classify(DiscoveryFailure.Stage.COLLECTION, error).code())
                            .isEqualTo(DiscoveryFailure.Code.COLLECTION_RESPONSE_INVALID));
            assertThatThrownBy(() -> provider.search("이어폰", 3)).isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        });
        assertThat(calls).hasValue(2);
    }

    @Test void providerRejectionIsDistinctFromEmptySearchWithoutEchoingResponse() {
        respond("/scrape", 200, "{\"success\":false,\"error\":\"fixture-provider-secret\"}");
        respond("/search", 200, "{\"success\":true,\"data\":{\"web\":[]}}");
        context.run(app -> {
            var provider = provider(app.getBean(RestClient.Builder.class));
            assertThatThrownBy(() -> provider.scrape("https://example.com/a"))
                    .isInstanceOf(WebEvidenceException.class).hasMessageNotContaining("fixture-provider-secret")
                    .satisfies(error -> assertThat(((WebEvidenceException) error).reason()).isEqualTo(WebEvidenceException.Reason.REJECTED));
            assertThat(provider.search("이어폰", 3)).isEmpty();
        });
        assertThat(calls).hasValue(2);
    }

    @Test void invalidSearchShapeIsNotReportedAsNoResults() {
        respond("/search", 200, "{\"success\":true,\"data\":{}}");
        context.run(app -> assertThatThrownBy(() -> provider(app.getBean(RestClient.Builder.class)).search("이어폰", 3))
                .isInstanceOf(WebEvidenceException.class));
        assertThat(calls).hasValue(1);
    }

    @Test void resendRejectsMalformedJsonWithoutRetry() {
        respond("/emails", 200, "not JSON");
        context.run(app -> {
            var provider = new ResendReminderDeliveryAdapter(new ReminderProperties(true, baseUrl, "fixture", "test@example.com"),
                    app.getBean(RestClient.Builder.class));
            assertThatThrownBy(() -> provider.send(UUID.randomUUID(), "recipient@example.com", "비교", "https://example.com/decision"))
                    .isInstanceOf(RestClientException.class);
        });
        assertThat(calls).hasValue(1);
    }

    private FirecrawlWebEvidenceProvider provider(RestClient.Builder builder) {
        return new FirecrawlWebEvidenceProvider(new WebProperties(true, baseUrl, "fixture", Duration.ofSeconds(1)), builder);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var response = exchange.getResponseBody()) { response.write(bytes); }
            exchange.close();
        });
    }
}
