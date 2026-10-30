package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

/** 고정된 공식 출처만 읽으며 리다이렉트와 크기 초과를 거절한다. */
@Component
public class QcySpecificationSource {
    private static final URI URL = URI.create("https://www.qcy.com/products/qcy-t13-pro");
    public String fetch() throws Exception {
        return fetch(URL);
    }
    public String fetchN60() throws Exception {
        return fetch(URI.create("https://www.qcy.com/products/qcy-melobuds-n60"));
    }
    public String fetchHt16() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=990"));
    }
    public String fetchT17App() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=563"));
    }
    public String fetchHt20() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=1028"));
    }
    public String fetchHt10() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=924"));
    }
    public String fetchT29() throws Exception {
        return fetch(URI.create("https://qcystore.co.kr/product/detail.html?product_no=709"));
    }
    private String fetch(URI url) throws Exception { return SpecificationPageFetcher.fetch(url); }
}
