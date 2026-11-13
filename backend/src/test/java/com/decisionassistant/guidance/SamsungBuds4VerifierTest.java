package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

public class SamsungBuds4VerifierTest {
    static final Instant AT=Instant.parse("2026-11-13T13:00:00Z");
    // 공개 DOM의 필요한 구조만 재구성했다. 실제 수집 결과와 구분한다.
    public static final String HTML="""
            <title>%s</title><link rel='canonical' href='%s'>
            <script type='application/ld+json'>{"@type":["WebPage","ItemPage"]}</script>
            <script type='application/ld+json'>{"@type":"ProductGroup","@id":"%s","hasVariant":[
              {"name":"갤럭시 버즈4|화이트","sku":"%s","color":"화이트","offers":{"@type":"Offer","url":"%s",
              "priceCurrency":"KRW","price":"220200","availability":"https://schema.org/InStock","itemCondition":"https://schema.org/NewCondition"}},
              {"name":"갤럭시 버즈4 프로|화이트","sku":"SM-R640NZWAKOO","color":"화이트","offers":{"price":"323100"}}]}</script>
            <p id='bcBottomMdlCode'>%s</p><div id='goodsInfo'><p class='pd_txt'>%s</p></div>
            <div><input type='radio' data-bc-opt-gb='30' checked><label><strong class='choice_tit'>갤럭시 버즈4</strong></label></div>
            <li><input type='radio' data-bc-opt-gb='10' checked><label><span class='color_txt'>화이트</span></label></li>
            <input id='buy_count' value=''><span aria-live='polite' id='buy_count_status'>1</span>
            <dt id='bcOrgPriceDt'>기준가</dt><span id='bcOrgPrice'>259,000</span><dt id='bcPriceNm'>혜택가</dt>
            <em id='bcPrice' data-price='220200'>220,200</em><em id='bcTotalPrice' data-price='220200'>220,200</em>
            <li id='defaultDiscountInfo' data-discount-amount='38800'><h5 class='list_tit'>즉시 할인</h5><span class='list_price'><em>-38,800</em> 원</span></li>
            <div><input id='chkPayBenefitCard' type='checkbox' disabled><span class='price'>금액대 미달성</span></div>
            <button id='btnDirect' data-cta-type='normal'>구매하기</button>
            <p>기간 : 2026.11.01 ~ 2026.11.22</p>
            """.formatted(SamsungBuds4Verifier.TITLE,SamsungBuds4Verifier.CANONICAL,SamsungBuds4Verifier.GROUP,
                    SamsungBuds4Verifier.SKU,SamsungBuds4Verifier.URL,SamsungBuds4Verifier.SKU,SamsungBuds4Verifier.SKU);
    public static final String SPEC_HTML="<title>"+SamsungBuds4Verifier.TITLE+"</title><link rel='canonical' href='"
            +SamsungBuds4Verifier.CANONICAL+"'><p>갤럭시 버즈4는 뛰어난 사운드 시스템 "
            +SamsungBuds4Verifier.ANC_QUOTE+"</p><p>"+SamsungBuds4Verifier.BASIC_QUOTE+"</p>";

    @Test void selectedVariantInstantDiscountAndNoCardBenefitMustAgree() {
        assertThat(SamsungBuds4Verifier.price(HTML,AT)).contains(new java.math.BigDecimal("220200"));
        assertThat(OfficialProductParser.inspect(SamsungBuds4Verifier.URL,new WebEvidenceProvider.Page("","",HTML+" ".repeat(2_000_000),SamsungBuds4Verifier.URL),AT,true).product()).isNotNull();
        assertThat(SamsungBuds4Verifier.price(HTML+" ".repeat(3_000_000),AT)).isEmpty();
        for(var bad:List.of(HTML.replace("'buy_count_status'>1","'buy_count_status'>2"),
                HTML.replace("data-price='220200'>220,200</em><em id='bcTotalPrice'","data-price='210200'>210,200</em><em id='bcTotalPrice'"),
                HTML.replace("data-discount-amount='38800'","data-discount-amount='38801'"),
                HTML.replace("즉시 할인</h5>","회원 쿠폰</h5>"),HTML.replace("checkbox' disabled","checkbox' checked"),
                HTML.replace("금액대 미달성","할인 적용"),HTML.replace("id='btnDirect'","id='btnDirect' disabled"),
                HTML.replace("<button id='btnDirect'","<div hidden><button id='btnDirect'").replace("</button>","</button></div>"),
                HTML.replace("class='color_txt'>화이트","class='color_txt'>블랙"),
                HTML.replace("class='choice_tit'>갤럭시 버즈4","class='choice_tit'>갤럭시 버즈4 프로"),
                HTML.replace("schema.org/InStock","schema.org/OutOfStock"),HTML.replace("\"priceCurrency\":\"KRW\"","\"priceCurrency\":\"USD\""),
                HTML.replace("기간 : 2026.11.01 ~ 2026.11.22","기간 : 2026.11.01 ~ 2026.11.14"),
                HTML+"<p>기간 : 2026.11.01 ~ 2026.11.30</p>",HTML+"<p id='bcBottomMdlCode'>other</p>"))
            assertThat(SamsungBuds4Verifier.price(bad,AT)).isEmpty();
        assertThat(SamsungBuds4Verifier.price(HTML,Instant.parse("2026-11-21T00:00:00Z"))).isEmpty();
    }

    @Test void onlyExactCatalogUrlAndItsKnownCanonicalAreAllowed() {
        var p=new WebEvidenceProvider.Page("","",HTML,SamsungBuds4Verifier.URL);
        assertThat(OfficialProductParser.inspect(SamsungBuds4Verifier.URL,p,AT,true).product()).isNotNull();
        assertThat(OfficialProductParser.inspect(SamsungBuds4Verifier.URL,p,AT,false).product()).isNull();
        var other=SamsungBuds4Verifier.URL.replace("SM-R540NZWAKOO","SM-R640NZWAKOO");
        assertThat(OfficialStorePolicy.store(other)).isEmpty();
        assertThat(OfficialStorePolicy.store(SamsungBuds4Verifier.URL+"&coupon=true")).isEmpty();
        assertThat(OfficialProductParser.inspect(SamsungBuds4Verifier.URL,new WebEvidenceProvider.Page("","",HTML,other),AT,true).product()).isNull();
    }

    @Test void osIndependentBasicAudioIsNarrowlyMappedWithoutPromotingAppSupport() {
        var fact=new ProductFact("호환성","Android/iOS",SamsungBuds4Verifier.URL,SamsungBuds4Verifier.BASIC_QUOTE,AT,"UNREVIEWED");
        assertThat(SamsungBuds4Verifier.specification(SPEC_HTML,fact)).isTrue();
        for(var platform:List.of("Android","iOS"))
            assertThat(CompatibilityMatcher.check(com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS,platform,List.of(fact),null,true).status()).isEqualTo(RequirementStatus.MEETS);
        var liveOffer=new OfficialOffer(SamsungBuds4Verifier.URL,"삼성닷컴",java.math.BigDecimal.ONE,"KRW",null,AT,"판매가",OfficialStorePolicy.VERSION);
        assertThat(CompatibilityMatcher.check(com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS,"iOS",List.of(fact),liveOffer,false).status()).isEqualTo(RequirementStatus.UNKNOWN);
        var app=new ProductFact("호환성","Android/iOS",SamsungBuds4Verifier.URL,"갤럭시 웨어러블 앱 Android 지원",AT,"UNREVIEWED");
        assertThat(CompatibilityMatcher.check(com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS,"Android",List.of(app),null,true).status()).isEqualTo(RequirementStatus.UNKNOWN);
        assertThat(SamsungBuds4Verifier.specification(SPEC_HTML.replace("SM-R540NZWAKOO","SM-R640NZWAKOO"),fact)).isFalse();
        assertThat(SamsungBuds4Verifier.specification(SPEC_HTML.replace("<p>","<script>").replace("</p>","</script>"),fact)).isFalse();
    }
}
