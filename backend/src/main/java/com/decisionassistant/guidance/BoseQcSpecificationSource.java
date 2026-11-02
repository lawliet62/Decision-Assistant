package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class BoseQcSpecificationSource {
    static final String URL = "https://www.bose.com/p/earbuds/bose-quietcomfort-headphones/QCEARB24-HEADPHONEIN.html";
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(URL)); }
}
