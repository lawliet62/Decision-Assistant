package com.decisionassistant.guidance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.jsoup.Jsoup;

@Component
public class AudioTechnicaSpecificationRefreshWorker {
    static final String QUOTE = "アクティブノイズキャンセリング（ANC）機能を新搭載";
    private final SpecificationRefreshRunner runner;
    public AudioTechnicaSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc, AudioTechnicaSpecificationSource source,
            PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "audio-technica-sq1tw2nc",
                source::fetch, AudioTechnicaSpecificationRefreshWorker::supports, (html, fact) -> matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact e) {
        return "ANC 지원".equals(e.name()) && "지원".equals(e.value()) && QUOTE.equals(e.quote())
                && AudioTechnicaSpecificationSource.URL.equals(e.sourceUrl());
    }
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, AudioTechnicaSpecificationSource.URL);
        if (doc.select("title").size() != 1 || !"完全ワイヤレスイヤホン『ATH-SQ1TW2NC』が登場｜オーディオテクニカ".equals(doc.title())) return false;
        var rows = doc.select("span.style_bold").stream().filter(e -> QUOTE.equals(e.ownText())).toList();
        return rows.size() == 1;
    }
}
