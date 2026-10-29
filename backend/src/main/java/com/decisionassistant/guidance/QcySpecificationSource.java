package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

/** 고정된 공식 출처만 읽으며 리다이렉트와 크기 초과를 거절한다. */
@Component
public class QcySpecificationSource {
    private static final URI URL = URI.create("https://www.qcy.com/products/qcy-t13-pro");
    public String fetch() throws Exception {
        return fetch(URL);
    }
    public String fetchN60() throws Exception {
        return fetch(URI.create("https://www.qcy.com/products/qcy-melobuds-n60"));
    }
    public String fetchHt16() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=990"));
    }
    public String fetchT17App() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=563"));
    }
    private String fetch(URI url) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(15)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("SOURCE_UNAVAILABLE");
            var bytes = body.readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IllegalStateException("SOURCE_TOO_LARGE");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
