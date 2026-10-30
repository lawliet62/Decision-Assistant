package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

/** 등록한 Apple 사양 URL만 요청한다. */
@Component
public class AppleSpecificationSource {
    public String fetch() throws Exception {
        return fetch("https://www.apple.com/kr/airpods-pro/specs/");
    }
    public String fetchAirpods5() throws Exception {
        return fetch("https://www.apple.com/kr/airpods-5/specs/");
    }
    private String fetch(String url) throws Exception { return SpecificationPageFetcher.fetch(URI.create(url)); }
}
