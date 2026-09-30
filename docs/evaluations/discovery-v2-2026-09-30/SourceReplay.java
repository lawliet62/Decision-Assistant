package com.decisionassistant.guidance;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import tools.jackson.databind.json.JsonMapper;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 수동 공개 출처 진단. 고정 JAR의 parser를 쓰며 HTML은 메모리에만 둔다. */
public class SourceReplay {
    public static void main(String[] args) throws Exception {
        var json = JsonMapper.builder().build();
        var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
        var results = new ArrayList<Map<String, Object>>();
        for (var url : List.of("https://qcystore.co.kr/product/detail.html?product_no=633",
                "https://qcystore.co.kr/product/detail.html?product_no=1033",
                "https://qcystore.co.kr/product/detail.html?product_no=563",
                "https://www.lge.co.kr/product/notebook/15u50u-growk")) {
            var response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
            var html = response.body(); var now = Instant.now(); var doc = Jsoup.parse(html, url);
            var inspected = OfficialProductParser.inspect(url, new WebEvidenceProvider.Page(doc.title(), "진단", html, response.uri().toString()), now);
            var row = new LinkedHashMap<String, Object>();
            row.put("url", url); row.put("checkedAt", now.toString()); row.put("httpStatus", response.statusCode());
            row.put("outcome", inspected.rejection() == null ? "ACCEPTED" : inspected.rejection().name());
            if (inspected.product() != null) row.put("priceQuote", inspected.product().priceQuote());
            if (url.contains("lge.co.kr")) {
                for (var script : doc.select("script[type=application/ld+json]")) {
                    var product = json.readTree(script.data());
                    if (!"Product".equals(product.path("@type").asText())) continue;
                    // 가격 제외 후 사양 추출만 분리 점검한다. 앱 후보로 승격하지 않는다.
                    var blocks = ProductEvidence.extract(doc, product, url, product.path("name").asText(), "diagnostic-product");
                    var facts = blocks.stream().filter(b -> Set.of("메모리", "저장공간", "OS").contains(b.factName()))
                            .map(b -> SpecNormalizer.fact(b, url, now)).toList();
                    row.put("isolatedFacts", facts);
                    row.put("isolatedChecks", MinimumRequirementMatcher.check(
                            com.decisionassistant.template.MinimumRequirements.forCategory(com.decisionassistant.decision.DecisionTypes.TemplateKey.LAPTOP),
                            Map.of("memory", "16", "storage", "512"), facts));
                }
            }
            results.add(row);
        }
        var output = json.writerWithDefaultPrettyPrinter().writeValueAsString(results);
        Files.writeString(Path.of("../validation-v2/source-replay.json"), output + "\n");
        System.out.println(output);
    }
}
