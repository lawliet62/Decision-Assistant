package com.decisionassistant.guidance;

public interface WebEvidenceProvider {
    record Page(String title, String markdown) {}
    Page scrape(String url);
    String name();
}
