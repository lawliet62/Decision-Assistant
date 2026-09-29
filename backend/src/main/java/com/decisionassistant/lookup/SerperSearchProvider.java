package com.decisionassistant.lookup;

import com.decisionassistant.guidance.WebSearchProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SerperSearchProvider implements WebSearchProvider {
    private final SearchProperties properties;
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public SerperSearchProvider(SearchProperties properties) {
        this(properties, URI.create("https://google.serper.dev/search"), Duration.ofSeconds(8));
    }

    // Test seam only: production destination is fixed; redirects cannot leak the API key.
    SerperSearchProvider(SearchProperties properties, URI endpoint, Duration timeout) {
        this.properties = properties;
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        if (!properties.configured()) throw new LookupException(503, "SEARCH_DISABLED", "제품 검색을 사용할 수 없습니다. URL 직접 추가를 이용하세요.");
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("X-API-KEY", properties.apiKey()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("q", query, "num", Math.min(5, Math.max(1, limit)), "gl", "kr", "hl", "ko"))))
                    .build();
            org.slf4j.LoggerFactory.getLogger(SerperSearchProvider.class).info(
                    "product_event=discovery_provider_call decision_id={} provider=serper", org.slf4j.MDC.get("discoveryDecisionId"));
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            var root = json.readTree(response.body());
            if (!root.path("organic").isArray()) throw unavailable();
            var results = new ArrayList<SearchResult>();
            for (var item : root.path("organic")) {
                if (results.size() >= Math.min(5, limit)) break;
                var title = item.path("title").asText("");
                var url = item.path("link").asText("");
                if (!title.isBlank() && !url.isBlank() && url.length() <= 2048) {
                    results.add(new SearchResult(truncate(title, 200), url, truncate(item.path("snippet").asText(""), 500)));
                }
            }
            return List.copyOf(results);
        } catch (HttpTimeoutException exception) {
            throw new LookupException(504, "SEARCH_TIMEOUT", "검색 응답이 늦어 중단했습니다. 잠시 후 다시 검색하거나 URL을 추가하세요.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            throw unavailable();
        }
    }

    private String truncate(String value, int max) { return value.substring(0, Math.min(value.length(), max)); }
    private LookupException unavailable() {
        return new LookupException(502, "SEARCH_PROVIDER_UNAVAILABLE", "검색 서비스에 연결할 수 없습니다. 잠시 후 다시 검색하거나 URL을 추가하세요.");
    }
    @Override public String name() { return "serper"; }
}
