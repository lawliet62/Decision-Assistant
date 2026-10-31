package com.decisionassistant.guidance;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class AnkerAeroclipSpecificationSource {
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(AnkerProductVerifier.AERO_URL)); }
}
