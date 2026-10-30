package com.decisionassistant.guidance;
import org.springframework.stereotype.Component;
import java.net.URI;
@Component
public class AnkerP41iSpecificationSource {
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(AnkerP41iVerifier.SPEC_URL)); }
}
