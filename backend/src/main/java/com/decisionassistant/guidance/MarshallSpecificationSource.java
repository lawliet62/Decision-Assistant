package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

/** 등록한 Marshall 상품 사양 URL만 요청한다. */
@Component
public class MarshallSpecificationSource {
    public String fetch() throws Exception {
        return fetch("https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450");
    }
    private String fetch(String url) throws Exception { return SpecificationPageFetcher.fetch(URI.create(url)); }
}
