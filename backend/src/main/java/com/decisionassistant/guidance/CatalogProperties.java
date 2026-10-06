package com.decisionassistant.guidance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("decision-assistant.catalog")
public record CatalogProperties(boolean enabled, boolean refreshEnabled, String manifest, int monthlyLimit) {
    public static final String VERSION = "catalog-v1";
    public static final String TEMPLATE = "intake-catalog-v1";
}
