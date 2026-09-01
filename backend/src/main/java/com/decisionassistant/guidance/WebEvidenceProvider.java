package com.decisionassistant.guidance;

import java.util.List;

public interface WebEvidenceProvider {
    record Page(String title, String markdown) {}
    record SearchResult(String title, String url, String description) {}
    Page scrape(String url);
    List<SearchResult> search(String query, int limit);
    String name();
}
