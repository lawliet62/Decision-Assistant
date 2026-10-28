package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

class OfficialProductParserTest {
    private static final String URL = "https://qcystore.co.kr/product/detail.html?product_no=1033";
    private static final String OFFER = "\"offers\":{\"@type\":\"Offer\",\"price\":21900,\"priceCurrency\":\"KRW\"}";
    private static final String PURCHASE = "<article itemscope itemtype='https://schema.org/Product'><h1 itemprop='name'>Buds</h1><table><tr><th>판매가</th><td>21,900원</td></tr></table></article>";
    private OfficialProduct parse(String data) { return parse(data, URL); }
    private OfficialProduct parse(String data, String resolved) {
        return OfficialProductParser.parse(URL, new WebEvidenceProvider.Page("Buds", "Buds ANC 지원", "<script type='application/ld+json'>" + data + "</script>" + PURCHASE, resolved), Instant.now());
    }
    private String product(String offer) { return "{\"@type\":\"Product\",\"name\":\"Buds\",\"sku\":\"buds-1\",\"image\":\"/web/product/buds.jpg\"," + offer + "}"; }

    @Test void applePriceRequiresSinglePurchaseLinkMatchingOfferSkuAndAmount() {
        var url = "https://www.apple.com/kr/shop/buy-airpods/airpods-pro-3";
        var data = "<title>AirPods Pro 3</title><script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"AirPods Pro 3\",\"url\":\"" + url + "\",\"offers\":{\"@type\":\"Offer\",\"sku\":\"MFHP4KH/A\",\"price\":369000,\"priceCurrency\":\"KRW\"}}</script>";
        var purchase = "<div class='product-selection-area'><a data-slot-name='productSelection' href='" + url + "?product=mfhp4kh/a&amp;step=select'><span class='current_price'>₩369,000</span></a></div>";
        var result = OfficialProductParser.inspect(url, new WebEvidenceProvider.Page("", "", data + purchase, url), Instant.now());
        assertThat(result.rejection()).isNull();
        assertThat(result.product().identity()).isEqualTo("apple-kr:mfhp4kh/a");
        for (var invalid : List.of(purchase.replace("mfhp4kh/a", "other"), purchase.replace("369,000", "199,000"), purchase + purchase, purchase.replace("₩369,000", "회원 ₩369,000"), purchase.replace("airpods-pro-3?", "airpods-5?"))) {
            assertThat(OfficialProductParser.inspect(url, new WebEvidenceProvider.Page("", "", data + invalid, url), Instant.now()).product()).isNull();
        }
    }

    @Test void validatesPriceImageAndOfficialIdentityFromCollectedHtml() {
        var product = parse(product(OFFER));
        assertThat(product.name()).isEqualTo("Buds");
        assertThat(product.offer().price()).isEqualByComparingTo("21900");
        assertThat(product.offer().imageUrl()).isEqualTo("https://qcystore.co.kr/web/product/buds.jpg");
        assertThat(product.offer().purchaseUrl()).isEqualTo(URL);
        assertThat(product.offer().policyVersion()).isEqualTo(OfficialStorePolicy.VERSION);
        assertThat(parse(product(OFFER).replace("/web/product/buds.jpg", "https://unverified.example/image.jpg")).offer().imageUrl()).isNull();
    }

    @Test void reportsRejectionWithoutChangingAcceptanceOrRetainingPageContent() {
        var html = "<script type='application/ld+json'>" + product(OFFER) + "</script>" + PURCHASE;
        var valid = new WebEvidenceProvider.Page("Buds", "Buds", html, URL);
        assertThat(OfficialProductParser.inspect(URL, valid, Instant.now()).rejection()).isNull();
        assertThat(OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", null, URL), Instant.now()).rejection())
                .isEqualTo(OfficialProductParser.Rejection.HTML_MISSING);
        assertThat(OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", html, null), Instant.now()).rejection())
                .isEqualTo(OfficialProductParser.Rejection.RESOLVED_URL_MISSING);
        assertThat(OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", "<p>21900원</p>", URL), Instant.now()).rejection())
                .isEqualTo(OfficialProductParser.Rejection.PRODUCT_MISSING);
        assertThat(OfficialProductParser.inspect(URL, new WebEvidenceProvider.Page("", "", html.replace("KRW", "USD"), URL), Instant.now()).rejection())
                .isEqualTo(OfficialProductParser.Rejection.CURRENCY_UNSUPPORTED);
    }
    @ParameterizedTest @ValueSource(strings = {
            "\"offers\":{\"@type\":\"AggregateOffer\",\"lowPrice\":1,\"priceCurrency\":\"KRW\"}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":1,\"priceCurrency\":\"USD\"}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":-1,\"priceCurrency\":\"KRW\"}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":1,\"priceCurrency\":\"KRW\",\"availability\":\"https://schema.org/OutOfStock\"}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":1,\"priceCurrency\":\"KRW\",\"eligibleCustomerType\":\"Member\"}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":1,\"priceCurrency\":\"KRW\",\"priceSpecification\":{\"name\":\"카드 혜택가\"}}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":1,\"priceCurrency\":\"KRW\",\"url\":\"https://unverified.example/buds\"}",
            "\"offers\":{\"@type\":\"Offer\",\"price\":1,\"priceCurrency\":\"KRW\",\"priceValidUntil\":\"2020-01-01\"}", "\"offers\":null"
    }) void rejectsNonComparablePrices(String offer) { assertThat(parse(product(offer))).isNull(); }

    @Test void rejectsAmbiguousProductsRedirectsAndUnverifiedStores() {
        assertThat(parse("[" + product(OFFER) + "," + product(OFFER).replace("Buds", "Case") + "]")).isNull();
        assertThat(parse(product(OFFER), "https://evil.example/product/1033")).isNull();
        assertThat(parse(product(OFFER), null)).isNull();
        assertThat(parse(product(OFFER), URL.replace("1033", "999"))).isNull();
        for (var url : List.of("https://qcystore.co.kr.evil.example/product/detail.html?product_no=1033", "https://qcystore.co.kr@evil.example/product/1033", "https://smartstore.naver.com/other/products/123", "http://qcystore.co.kr/product/detail.html?product_no=1033", "https://qcystore.co.kr/product/%2e%2e/detail.html?product_no=1033"))
            assertThat(OfficialStorePolicy.store(url)).isEmpty();
        assertThat(OfficialStorePolicy.store("https://smartstore.naver.com/ylintl/products/123")).isPresent();
    }
    @Test void acceptsSamePriceColorVariantsButNotCapacityOrDifferentPrices() {
        var variants = "{\"@type\":\"ProductGroup\",\"name\":\"Buds\",\"productGroupID\":\"model-a\",\"variesBy\":[\"color\"],\"hasVariant\":[" + product(OFFER) + "," + product(OFFER) + "]}";
        assertThat(parse(variants).offer().price()).isEqualByComparingTo("21900");
        assertThat(parse(variants.replace("color", "size"))).isNull();
        assertThat(parse(variants.replaceFirst("21900", "12900"))).isNull();
        assertThat(OfficialProductParser.samePage(URL, "https://qcystore.co.kr/product/이어폰/1033/category/1/")).isTrue();
    }
    @Test void categoriesHaveSearchScopesAndSourceKindsDoNotCertifyLegacyPages() {
        for (var key : com.decisionassistant.decision.DecisionTypes.TemplateKey.values()) {
            if (key != com.decisionassistant.decision.DecisionTypes.TemplateKey.CUSTOM) assertThat(OfficialStorePolicy.searchScope(key)).contains("site:");
        }
        assertThat(OfficialStorePolicy.sourceKind(URL, false)).isEqualTo(OfficialStorePolicy.SourceKind.WEB);
        assertThat(OfficialStorePolicy.sourceKind("https://youtu.be/abc", false)).isEqualTo(OfficialStorePolicy.SourceKind.VIDEO);
        assertThat(OfficialStorePolicy.sourceKind("https://review.tistory.com/1", false)).isEqualTo(OfficialStorePolicy.SourceKind.BLOG);
    }
    @Test void rankingUsesPriceAndSeparatesMissingRequirementsAndBudget() {
        var cheap = parse(product(OFFER)).offer();
        assertThat(DiscoveryRanking.group(cheap, List.of(), null)).isEqualTo(DiscoveryRanking.Group.RECOMMENDED);
        assertThat(DiscoveryRanking.group(cheap, List.of(), new java.math.BigDecimal("20000"))).isEqualTo(DiscoveryRanking.Group.BELOW);
        assertThat(DiscoveryRanking.group(cheap, List.of(new RequirementCheck("anc", "ANC 지원", "지원 필수", RequirementStatus.UNKNOWN, List.of(), UnknownReason.MISSING)), null))
                .isEqualTo(DiscoveryRanking.Group.UNKNOWN);
        assertThat(DiscoveryRanking.group(null, List.of(), null)).isEqualTo(DiscoveryRanking.Group.LEGACY);
    }
}
