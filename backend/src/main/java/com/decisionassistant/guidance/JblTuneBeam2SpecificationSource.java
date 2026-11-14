package com.decisionassistant.guidance;

@org.springframework.stereotype.Component
public class JblTuneBeam2SpecificationSource {
    public String fetch() throws Exception {
        return SpecificationPageFetcher.fetch(java.net.URI.create(JblTuneBeam2Verifier.SPEC_URL));
    }
}
