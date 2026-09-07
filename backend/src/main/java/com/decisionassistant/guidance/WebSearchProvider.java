package com.decisionassistant.guidance;

import java.util.List;

public interface WebSearchProvider {
    record SearchResult(String title, String url, String description) {}
    List<SearchResult> search(String query, int limit);
    String name();
}
