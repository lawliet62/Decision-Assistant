package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppleSpecificationVerifierTest {
    @Test void basicIosRequiresOwnCanonicalAndCompleteUnambiguousRequirements() {
        for (var five : java.util.List.of(false,true)) {
            var model=five ? "AirPods 5" : "AirPods Pro 3";
            var url="https://www.apple.com/kr/"+(five?"airpods-5":"airpods-pro")+"/specs/";
            var page="<title>"+model+" - 제품 사양 - Apple (KR)</title><link rel='canonical' href='"+url+"'>"
                    +"<div class='techspecs-section section-requirements'><ul class='techspecs-list'><li>"+AppleSpecificationVerifier.IOS_QUOTE+"</li>"
                    +"<li>iPad</li><li>Watch</li><li>Mac</li><li>TV</li><li>Vision</li><li>"+AppleSpecificationVerifier.BASIC_AUDIO+"</li></ul></div>";
            assertThat(five?AppleSpecificationVerifier.matchesAirpods5(page,"호환성"):AppleSpecificationVerifier.matches(page,"호환성")).isTrue();
            for(var bad:java.util.List.of(page.replace("iOS가", "iOS 미지원이"),page.replace("/specs/", "/other/"),
                    page.replace(AppleSpecificationVerifier.BASIC_AUDIO,"기본 연결 미지원"),page+"<title>다른 모델</title>",page.replace("section-requirements","section-app")))
                assertThat(five?AppleSpecificationVerifier.matchesAirpods5(bad,"호환성"):AppleSpecificationVerifier.matches(bad,"호환성")).isFalse();
            var fact=new DiscoveryContracts.ProductFact("호환성","iOS",url,AppleSpecificationVerifier.IOS_QUOTE,java.time.Instant.now(),"UNREVIEWED");
            var category=com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS;
            assertThat(CompatibilityMatcher.check(category,"iOS",java.util.List.of(fact),null,true).status()).isEqualTo(DiscoveryContracts.RequirementStatus.MEETS);
            assertThat(CompatibilityMatcher.check(category,"Android",java.util.List.of(fact),null,true).status()).isEqualTo(DiscoveryContracts.RequirementStatus.UNKNOWN);
            assertThat(CompatibilityMatcher.check(category,"iOS",java.util.List.of(fact),null,false)).isNull();
            var disputed=new DiscoveryContracts.ProductFact("호환성","iOS",url,AppleSpecificationVerifier.IOS_QUOTE,java.time.Instant.now(),"DISPUTED");
            assertThat(CompatibilityMatcher.check(category,"iOS",java.util.List.of(disputed),null,true).status()).isEqualTo(DiscoveryContracts.RequirementStatus.UNKNOWN);
        }
    }
    private static final String PAGE = """
            <title>AirPods Pro 3 - 제품 사양 - Apple (KR)</title>
            <div class='techspecs-section section-audio'><ul class='techspecs-list'><li>액티브 노이즈 캔슬링</li></ul></div>
            <div class='techspecs-section section-water'><ul class='techspecs-list'><li>먼지, 땀, 습기에 강한 방진 및 생활 방수 디자인(IP57): AirPods&nbsp;Pro&nbsp;및 충전 케이스</li></ul></div>
            """;
    @Test void acceptsOnlyExactModelAndUnambiguousSpecificationSection() {
        assertThat(AppleSpecificationVerifier.matches(PAGE, "ANC 지원")).isTrue();
        assertThat(AppleSpecificationVerifier.matches(PAGE, "방수")).isTrue();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("AirPods Pro 3", "AirPods Pro 4"), "방수")).isFalse();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("IP57", "IP54"), "방수")).isFalse();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("<li>액티브 노이즈 캔슬링</li>", "<li>액티브 노이즈 캔슬링</li><li>액티브 노이즈 캔슬링 미지원</li>"), "ANC 지원")).isFalse();
        assertThat(AppleSpecificationVerifier.matches(PAGE.replace("section-water", "section-other") + "<aside>IP57</aside>", "방수")).isFalse();
    }
    @Test void airpods5RequiresItsOwnModelAndSpecification() {
        var page = PAGE.replace("AirPods Pro 3", "AirPods 5").replace(": AirPods&nbsp;Pro&nbsp;및 충전 케이스", "");
        assertThat(AppleSpecificationVerifier.matchesAirpods5(page, "ANC 지원")).isTrue();
        assertThat(AppleSpecificationVerifier.matchesAirpods5(page, "방수")).isTrue();
        assertThat(AppleSpecificationVerifier.matchesAirpods5(PAGE, "ANC 지원")).isFalse();
        assertThat(AppleSpecificationVerifier.matchesAirpods5(page.replace("IP57", "IP54"), "방수")).isFalse();
        assertThat(AppleSpecificationVerifier.matchesAirpods5(page.replace("액티브 노이즈 캔슬링</li>", "액티브 노이즈 캔슬링 미지원</li>"), "ANC 지원")).isFalse();
    }
}
