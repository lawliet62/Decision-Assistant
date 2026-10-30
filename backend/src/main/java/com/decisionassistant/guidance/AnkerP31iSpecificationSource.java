package com.decisionassistant.guidance;

import org.springframework.stereotype.Component;
import java.net.URI;

/** 확인한 제조사 고정 모델 페이지만 읽는다. */
@Component
public class AnkerP31iSpecificationSource {
    public String fetch() throws Exception { return SpecificationPageFetcher.fetch(URI.create(AnkerP31iSpecificationVerifier.URL)); }
}
