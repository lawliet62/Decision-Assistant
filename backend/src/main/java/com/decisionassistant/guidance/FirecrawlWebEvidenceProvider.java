package com.decisionassistant.guidance;

import com.fasterxml.jackson.databind.JsonNode;
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
        this.client = builder.baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public Page scrape(String url) {
        requireConfigured();
        JsonNode response = client.post().uri("/scrape").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "url", url,
                        "formats", List.of("markdown"),
                        "onlyMainContent", true,
                        "storeInCache", false,
                        "removeBase64Images", true,
                        "timeout", millis(properties.timeout(), Duration.ofSeconds(45))))
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("success").asBoolean()) throw new IllegalStateException("웹 페이지를 가져오지 못했습니다.");
        var data = response.path("data");
        var title = data.path("metadata").path("title").asText("");
        return new Page(title, data.path("markdown").asText(""));
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        requireConfigured();
        JsonNode response = client.post().uri("/search").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "limit", limit, "country", "KR", "ignoreInvalidURLs", true))
                .retrieve().body(JsonNode.class);
        var results = new ArrayList<SearchResult>();
        if (response == null || !response.path("success").asBoolean()) return results;
        for (var item : response.path("data").path("web")) {
            results.add(new SearchResult(item.path("title").asText("대안 후보"), item.path("url").asText(),
                    item.path("description").asText("현재 후보군과 비교할 수 있는 대안입니다.")));
        }
        return List.copyOf(results);
    }

    @Override public String name() { return "firecrawl"; }
    private void requireConfigured() { if (!properties.configured()) throw new IllegalStateException("웹 수집 provider가 비활성화되어 있습니다."); }
    private int millis(Duration value, Duration fallback) { return Math.toIntExact((value == null ? fallback : value).toMillis()); }
}
