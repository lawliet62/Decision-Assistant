package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SonyC710nVerifierTest {
    // 실제 DOM에서 가격·구성 검증에 필요한 짧은 구조만 재구성했다.
    static final String HTML = """
            <title>WF-C710N : 오디오 : 무선 이어폰</title>
            <div class='product_view_about'><p class='product_tit'>WF-C710N</p>
            <div class='product_price'><div class='original'><span class='price'>149,000</span>원</div>
            <div class='sale'><strong class='price'>139,000</strong>원</div></div>
            <div class='result_btn_box'><a class='direct' href='/order/sheet'>바로 구매</a></div></div>
            <a class='opt_list'>WF-C710N / 글래스 블루</a><a class='opt_list'>WF-C710N / 블랙</a>
            <a class='opt_list'>WF-C710N / 화이트</a><a class='opt_list'>WF-C710N / 핑크</a>
            <div class='selected_opt'><div class='opt_info'><span class='opt_name'>WF-C710N / 글래스 블루</span>
            <input class='count' value='1'><p class='opt_price'><strong class='price'>139,000</strong>원</p>
            </div></div>
            """;

    @Test void requiresOneSelectedBlueItemWithMatchingPublicPrice() {
        assertThat(SonyC710nVerifier.price(HTML)).contains(new java.math.BigDecimal("139000"));
        assertThat(SonyC710nVerifier.price(HTML.replace("href='/order/sheet'", "href='https://store.sony.co.kr/order/sheet'")))
                .contains(new java.math.BigDecimal("139000"));
        for (var invalid : List.of(HTML.replace("class='opt_info'", "class='unselected'"),
                HTML.replace("value='1'", "value='2'"), HTML.replace("class='opt_name'>WF-C710N / 글래스 블루", "class='opt_name'>WF-C710N / 블랙"),
                HTML.replace("class='sale'", "class='member'"), HTML.replace("139,000</strong>원</p>", "130,000</strong>원</p>"),
                HTML.replace("class='opt_list'>WF-C710N / 글래스 블루", "class='opt_list disabled'>WF-C710N / 글래스 블루"),
                HTML.replace("class='direct'", "class='direct' aria-disabled='true'"),
                HTML.replace("139,000</strong>원</div>", "139,000</strong>원 회원가</div>"),
                HTML + "<div class='selected_opt'></div>", HTML.replace("WF-C710N", "WF-C700N"),
                HTML.replace("href='/order/sheet'", "href='https://evil.example/order/sheet'"),
                HTML.replace("href='/order/sheet'", "href='https://store.sony.co.kr/order/sheet?other=1'"),
                HTML.replace("href='/order/sheet'", "href='http://store.sony.co.kr/order/sheet'")))
            assertThat(SonyC710nVerifier.price(invalid)).isEmpty();
    }

    @Test void catalogOnlyAndExactResolvedUrlAreRequired() {
        var url=SonyC710nVerifier.URL;
        var page=new WebEvidenceProvider.Page("", "", HTML, url);
        assertThat(OfficialProductParser.inspect(url,page,Instant.now(),true).product().identity()).isEqualTo(SonyC710nVerifier.IDENTITY);
        assertThat(OfficialProductParser.inspect(url,page,Instant.now(),false).product()).isNull();
        assertThat(OfficialProductParser.inspect(url,new WebEvidenceProvider.Page("","",HTML,url+"0"),Instant.now(),true).product()).isNull();
        assertThat(OfficialStorePolicy.store(url+"?color=black")).isEmpty();
        assertThat(OfficialStorePolicy.store("https://store.sony.co.kr/product-view/128838585")).isEmpty();
    }

    @Test void specificationNeedsExactSourceModelTitleAndQuote() {
        var fact = new DiscoveryContracts.ProductFact("ANC 지원", "지원", SonyC710nVerifier.SPEC_URL,
                SonyC710nVerifier.ANC_QUOTE, Instant.now(), "UNREVIEWED", null);
        var html = "<title>WF-C710N 무선 노이즈 캔슬링 이어폰 | 30시간 배터리 | 소니코리아</title>"
                + "<link rel='canonical' href='" + SonyC710nVerifier.SPEC_URL + "'><p>" + SonyC710nVerifier.ANC_QUOTE + "</p>";
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(SonyC710nVerifier.specification(json.writeValueAsString(java.util.Map.of(SonyC710nVerifier.SPEC_URL,html)), fact)).isTrue();
        for (var invalid : List.of(html.replace("WF-C710N", "WF-C700N"), html.replace("<p>", "<script>").replace("</p>", "</script>"),
                html.replace("rel='canonical'", "rel='alternate'")))
            assertThat(SonyC710nVerifier.specification(json.writeValueAsString(java.util.Map.of(SonyC710nVerifier.SPEC_URL,invalid)), fact)).isFalse();
    }
}
