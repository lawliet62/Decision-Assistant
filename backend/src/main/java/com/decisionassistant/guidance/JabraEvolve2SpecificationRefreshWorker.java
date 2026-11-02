package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class JabraEvolve2SpecificationRefreshWorker {
    static final String QUOTE = "Adjustable Active Noise Cancellation";
    private static final String TITLE = "Jabra unveils professional, pocketable true wireless earbuds engineered for hybrid and remote working";
    private final SpecificationRefreshRunner runner;

    public JabraEvolve2SpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc, JabraEvolve2SpecificationSource source,
            PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "jabra-evolve2-buds-usba",
                source::fetch, JabraEvolve2SpecificationRefreshWorker::supports, (html, fact) -> matches(html));
    }
    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }
    private static boolean supports(DiscoveryContracts.ProductFact fact) {
        return "ANC 지원".equals(fact.name()) && "지원".equals(fact.value()) && QUOTE.equals(fact.quote())
                && JabraEvolve2SpecificationSource.URL.equals(fact.sourceUrl());
    }
    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, JabraEvolve2SpecificationSource.URL);
        var canonical = doc.select("link[rel=canonical]"); var headings = doc.select("h1");
        if (canonical.size() != 1 || !JabraEvolve2SpecificationSource.URL.equals(canonical.first().absUrl("href"))
                || !TITLE.equals(doc.title()) || headings.size() != 1 || !TITLE.equals(headings.first().text())) return false;
        var root = headings.first().parent();
        if (root == null || !root.text().contains("Launches new Evolve2 Buds")) return false;
        return root.select("li strong").stream().filter(e -> QUOTE.equals(e.text()) && e.parent() != null
                && e.parent().text().startsWith(QUOTE + " - filters out background noise")).count() == 1;
    }
}
