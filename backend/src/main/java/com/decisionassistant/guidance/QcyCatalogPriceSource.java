package com.decisionassistant.guidance;

import java.net.URI;
import org.springframework.stereotype.Component;

/** 개발용 직접 수집은 사전 검토한 CT06 고정 상품 한 건만 허용한다. */
@Component
public class QcyCatalogPriceSource {
    public static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=994";
    public static boolean supports(String url) { return URL.equals(url); }
    public WebEvidenceProvider.Page fetch(String url) throws Exception {
        if (!supports(url)) throw new IllegalArgumentException("UNREGISTERED_DIRECT_SOURCE");
        var html = SpecificationPageFetcher.fetch(URI.create(URL));
        // 직접 조회기는 redirect를 거절하므로 응답 주소는 요청한 고정 주소다.
        return new WebEvidenceProvider.Page("", "", html, URL);
    }
}
