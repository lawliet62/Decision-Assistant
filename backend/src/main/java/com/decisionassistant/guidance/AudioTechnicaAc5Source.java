package com.decisionassistant.guidance;
import org.springframework.stereotype.Component;
import java.net.URI;
@Component
public class AudioTechnicaAc5Source {
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(AudioTechnicaProductVerifier.AC5_URL)); }
}
