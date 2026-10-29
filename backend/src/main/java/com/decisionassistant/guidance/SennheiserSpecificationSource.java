package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class SennheiserSpecificationSource {
    public String fetch() throws Exception {
        return SpecificationPageFetcher.fetch(URI.create("https://kr.sennheiser-hearing.com/product/detail.html?product_no=731"));
    }
}
