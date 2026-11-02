package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class AudioTechnicaSpecificationSource {
    static final String URL = "https://www.audio-technica.co.jp/corp/news/detail/1350";
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(URL)); }
}
