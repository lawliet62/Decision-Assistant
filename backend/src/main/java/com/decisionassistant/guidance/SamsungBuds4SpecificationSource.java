package com.decisionassistant.guidance;
@org.springframework.stereotype.Component
public class SamsungBuds4SpecificationSource {
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(java.net.URI.create(SamsungBuds4Verifier.URL)); }
}
