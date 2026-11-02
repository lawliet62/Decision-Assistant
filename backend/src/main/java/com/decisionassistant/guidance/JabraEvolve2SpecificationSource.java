package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class JabraEvolve2SpecificationSource {
    static final String URL = "https://www.jabra.com/ko-kr/about/news-and-press-releases/2022-jabra-evolve2-buds";
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(URL)); }
}
