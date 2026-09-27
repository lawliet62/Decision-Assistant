package com.decisionassistant.guidance;

import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FirecrawlWebEvidenceProvider implements WebEvidenceProvider, WebSearchProvider {
    private final WebProperties properties;
    private final RestClient client;

    public FirecrawlWebEvidenceProvider(WebProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).version(java.net.http.HttpClient.Version.HTTP_1_1)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout((properties.timeout() == null ? Duration.ofSeconds(45) : properties.timeout()).plusSeconds(5));
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public Page scrape(String url) {
        return scrape(url, false);
    }
    @Override public Page scrapeProduct(String url) { return scrape(url, true); }
    private Page scrape(String url, boolean product) {
        requireConfigured();
        var request = new java.util.HashMap<String, Object>(Map.of(
                        "url", url,
                        "formats", product ? List.of("markdown", "rawHtml") : List.of("markdown"),
                        "onlyMainContent", true,
                        "storeInCache", false,
                        "removeBase64Images", true,
                        "timeout", millis(properties.timeout(), Duration.ofSeconds(45))));
        if (product) request.put("maxAge", 0);
        JsonNode response = client.post().uri("/scrape").contentType(MediaType.APPLICATION_JSON).body(request)
                .retrieve().body(JsonNode.class);
        var data = successfulData(response);
        if (!data.path("markdown").isString()) throw new WebEvidenceException(WebEvidenceException.Reason.RESPONSE_INVALID);
        var title = data.path("metadata").path("title").asText("");
        return new Page(title, data.path("markdown").asText(""), data.path("rawHtml").asText(null),
                data.path("metadata").path("url").asText(null));
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        requireConfigured();
        JsonNode response = client.post().uri("/search").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "limit", limit, "country", "KR", "ignoreInvalidURLs", true))
                .retrieve().body(JsonNode.class);
        var results = new ArrayList<SearchResult>();
        var data = successfulData(response);
        if (!data.path("web").isArray()) throw new WebEvidenceException(WebEvidenceException.Reason.RESPONSE_INVALID);
        for (var item : data.path("web")) {
            results.add(new SearchResult(item.path("title").asText("대안 후보"), item.path("url").asText(),
                    item.path("description").asText("현재 후보군과 비교할 수 있는 대안입니다.")));
        }
        return List.copyOf(results);
    }

    @Override public String name() { return "firecrawl"; }
    private JsonNode successfulData(JsonNode response) {
        if (response == null || !response.path("success").isBoolean())
            throw new WebEvidenceException(WebEvidenceException.Reason.RESPONSE_INVALID);
        if (!response.path("success").asBoolean()) throw new WebEvidenceException(WebEvidenceException.Reason.REJECTED);
        var data = response.path("data");
        if (!data.isObject()) throw new WebEvidenceException(WebEvidenceException.Reason.RESPONSE_INVALID);
        return data;
    }
    private void requireConfigured() { if (!properties.configured()) throw new IllegalStateException("웹 수집 provider가 비활성화되어 있습니다."); }
    private int millis(Duration value, Duration fallback) { return Math.toIntExact((value == null ? fallback : value).toMillis()); }
}
