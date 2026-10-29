package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 등록한 Apple 사양 URL만 요청한다. */
@Component
public class AppleSpecificationSource {
    public String fetch() throws Exception {
        return fetch("https://www.apple.com/kr/airpods-pro/specs/");
    }
    public String fetchAirpods5() throws Exception {
        return fetch("https://www.apple.com/kr/airpods-5/specs/");
    }
    private String fetch(String url) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("SOURCE_UNAVAILABLE");
            var bytes = body.readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IllegalStateException("SOURCE_TOO_LARGE");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
