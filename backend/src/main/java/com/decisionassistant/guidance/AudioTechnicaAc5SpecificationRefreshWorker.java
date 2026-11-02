package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AudioTechnicaAc5SpecificationRefreshWorker {
    static final String QUOTE = "무선 오픈이어 이어폰";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final SpecificationRefreshRunner runner;
    public AudioTechnicaAc5SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc, AudioTechnicaAc5Source source, PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "audio-technica-ac5tw",
                source::fetch, AudioTechnicaAc5SpecificationRefreshWorker::supports, (html, fact) -> matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "형태".equals(e.name()) && "오픈형".equals(e.value()) && QUOTE.equals(e.quote()) && AudioTechnicaProductVerifier.AC5_URL.equals(e.sourceUrl());
    }
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, AudioTechnicaProductVerifier.AC5_URL); var canonical = doc.select("link[rel=canonical]"); var roots = doc.select(".xans-product-detail");
        if (canonical.size() != 1 || !AudioTechnicaProductVerifier.AC5_URL.equals(canonical.first().absUrl("href")) || roots.size() != 1) return false;
        var titles = roots.first().select(".detail_name h2"); var details = roots.first().select(".detail_name p");
        if (titles.size() != 1 || !AudioTechnicaProductVerifier.AC5_NAME.equals(titles.first().text()) || details.size() != 1 || !QUOTE.equals(details.first().text())) return false;
        int groups = 0;
        for (var script : doc.select("script[type=application/ld+json]")) {
            try { var item = JSON.readTree(script.data()); if ("ProductGroup".equals(item.path("@type").asText(""))) {
                if (!AudioTechnicaProductVerifier.AC5_NAME.equals(item.path("name").asText("")) || !"cafe24_audiotechnica_1_921".equals(item.path("productGroupID").asText(""))) return false;
                groups++;
            }} catch (RuntimeException e) { return false; }
        }
        return groups == 1;
    }
}
