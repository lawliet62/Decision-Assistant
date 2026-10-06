package com.decisionassistant.lookup;

import com.decisionassistant.guidance.WebProperties;
import com.decisionassistant.guidance.WebSearchProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-lookups")
public class ProductLookupController {
    private final ProductLookupService service;
    private final SearchProperties search;
    private final WebProperties web;
    private final com.decisionassistant.guidance.CatalogProperties catalog;

    public ProductLookupController(ProductLookupService service, SearchProperties search, WebProperties web, com.decisionassistant.guidance.CatalogProperties catalog) {
        this.service = service;
        this.search = search;
        this.web = web;
        this.catalog = catalog;
    }

    public record LookupRequest(@NotBlank @Size(max = 200) String query, @NotNull @AssertTrue Boolean searchConsent) {}
    public record LookupResponse(List<WebSearchProvider.SearchResult> results) {}
    public record LookupCapabilities(boolean searchEnabled, String searchProvider, String searchNotice,
                                     boolean importEnabled, String importProvider, String importNotice,
                                     com.decisionassistant.guidance.OfficialStorePolicy.Description discoveryPolicy, boolean catalogEnabled) {}

    @GetMapping("/capabilities")
    public LookupCapabilities lookupCapabilities() {
        return new LookupCapabilities(search.configured(), "Serper",
                "입력한 제품명만 Serper 검색 API로 전송합니다. 개인정보·민감정보를 입력하지 마세요. 검색어와 결과 목록은 서버에 저장하지 않습니다.",
                web.configured(), "Firecrawl",
                "선택한 공개 URL을 Firecrawl로 보내 페이지 정보를 가져옵니다. 이후 추가한 URL과 기존 대안 검색에도 적용됩니다. 출처·짧은 발췌·조회 시각을 저장하며 가격·재고·공식 유통을 보장하지 않습니다.",
                catalog.enabled() ? new com.decisionassistant.guidance.OfficialStorePolicy.Description("catalog-v1", "CURATED_EARBUDS", "OBSERVED_KRW_PRICE_24H")
                        : com.decisionassistant.guidance.OfficialStorePolicy.description(), catalog.enabled());
    }

    @PostMapping
    public LookupResponse lookup(@Valid @RequestBody LookupRequest request) {
        return new LookupResponse(service.lookup(request.query().strip()));
    }
}
