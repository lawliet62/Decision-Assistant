package com.decisionassistant.guidance;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class AnkerLibertySpecificationSource {
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(AnkerLibertySpecificationVerifier.URL)); }
}
