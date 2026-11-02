package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class BoseQcSpecificationRefreshWorker {
    static final String QUOTE = "Active Noise Cancellation";
    private static final String DESCRIPTION = "New Bose QuietComfort Earbuds offer iconic audio, renowned noise cancellation";
    private final SpecificationRefreshRunner runner;

    public BoseQcSpecificationRefreshWorker(@Value("${decision-assistant.catalog.spec-refresh-enabled:false}") boolean enabled,
            CatalogProperties config, CatalogProductRepository products, JdbcTemplate jdbc, BoseQcSpecificationSource source,
            PlatformTransactionManager manager) {
        runner = new SpecificationRefreshRunner(enabled, config, products, jdbc, manager, "bose-qc-earbuds-ice-blue",
                source::fetch, BoseQcSpecificationRefreshWorker::supports, (html, fact) -> matches(html));
    }

    @Scheduled(fixedDelayString = "${decision-assistant.catalog.spec-poll-interval:3600000}")
    public void refresh() { runner.refresh(); }

    private static boolean supports(DiscoveryContracts.ProductFact fact) {
        return "ANC 지원".equals(fact.name()) && "지원".equals(fact.value()) && QUOTE.equals(fact.quote())
                && BoseQcSpecificationSource.URL.equals(fact.sourceUrl());
    }

    static boolean matches(String html) {
        if (html == null || html.length() > 2_000_000) return false;
        var doc = Jsoup.parse(html, BoseQcSpecificationSource.URL);
        var canonical = doc.select("link[rel=canonical]");
        var headings = doc.select("h1.product-name");
        var descriptions = doc.select(".product-description__content");
        var colors = doc.select("input.color-attribute[data-color='Ice Blue'][id=iceblue-qcearb24-headphonein-ddpmnt-ww]");
        var comparison = doc.select("li.product-carousel__item").stream().filter(e ->
                e.select("a.image-container-product-url-label[href=/p/earbuds/bose-quietcomfort-headphones/QCEARB24-HEADPHONEIN.html]")
                        .stream().anyMatch(a -> "Bose QuietComfort Earbuds".equals(a.text()))).toList();
        if (comparison.size() != 1) return false;
        var labels = comparison.getFirst().select("p.product-attribute-name").stream()
                .filter(e -> "Active Noise Cancellation".equals(e.ownText().strip())).toList();
        if (labels.size() != 1 || labels.getFirst().nextElementSibling() == null
                || !labels.getFirst().nextElementSibling().hasClass("product-attribute-value")
                || !"Yes".equals(labels.getFirst().nextElementSibling().text())) return false;
        return canonical.size() == 1 && BoseQcSpecificationSource.URL.equals(canonical.first().absUrl("href"))
                && headings.size() == 1 && "Bose QuietComfort Earbuds".equals(headings.first().text())
                && colors.size() == 1 && descriptions.size() == 1 && descriptions.first().text().contains(DESCRIPTION);
    }
}
