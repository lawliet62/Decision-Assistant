package com.decisionassistant.guidance;

public interface WebEvidenceProvider {
    record Page(String title, String markdown, String html, String resolvedUrl) {
        public Page(String title, String markdown) { this(title, markdown, null, null); }
    }
    Page scrape(String url);
    default Page scrapeProduct(String url) { return scrape(url); }
    String name();
}
