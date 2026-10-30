package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BoseProductVerifierTest {
    private static final String URL = BoseProductVerifier.URL;
    private static final String NAME = BoseProductVerifier.NAME;
    private static final String OPTION = "O20260113696597912a514";
    private static final String PAGE = "<link rel='canonical' href='" + URL + "'>"
            + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"" + NAME
            + "\",\"brand\":{\"name\":\"BOSE\"},\"offers\":{\"@type\":\"Offer\",\"price\":229000,\"priceCurrency\":\"KRW\","
            + "\"url\":\"https://bose.co.kr/shop_view/?idx=307\",\"availability\":\"http://schema.org/InStock\"}}</script>"
            + "<div id='prod_goods_form'><h1 class='view_tit'>" + NAME + "</h1><div class='pay_detail'><span class='real_price'>229,000원</span></div>"
            + "<div id='prod_options'><label data-opttype='color' data-optcode='" + OPTION + "' data-title='아이스 블루 (229,000원)'>"
            + "<input type='radio' value='" + OPTION + "'></label></div><div class='buy_btns pc'><a class='_btn_buy' data-bs-prod-code='s2026011323a13db67b522'>구매하기</a></div></div>";
    @Test void requiresExactProductSingleOptionAndPurchasePrice() {
        assertThat(BoseProductVerifier.price(PAGE)).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("229000"));
        for (var invalid : java.util.List.of(PAGE.replace("307", "308"), PAGE.replace("BOSE", "OTHER"), PAGE.replace("InStock", "OutOfStock"),
                PAGE.replace("229,000원</span>", "199,000원</span>"), PAGE.replace("229,000원)", "199,000원)"),
                PAGE.replace("type='radio'", "type='radio' disabled"), PAGE.replace("s2026011323a13db67b522", "other"),
                PAGE.replace("</label>", "</label><label data-opttype='color' data-optcode='other'></label>"),
                PAGE.replace("KRW", "USD"), PAGE.replace("<a class='_btn_buy'", "<a disabled class='_btn_buy'"),
                PAGE.replace("buy_btns pc", "buy_btns pc displaynone"), PAGE.replace("class='real_price'", "class='real_price' style='display:none'")))
            assertThat(BoseProductVerifier.price(invalid)).isEmpty();
    }
    @Test void policyRestrictsTheCatalogToTheExactProduct() {
        assertThat(OfficialStorePolicy.store(URL)).isPresent();
        assertThat(OfficialStorePolicy.store(URL.replace("307", "426"))).isEmpty();
        assertThat(OfficialStorePolicy.store(URL + "&other=1")).isEmpty();
        var page = new WebEvidenceProvider.Page("", "", PAGE, URL);
        assertThat(OfficialProductParser.inspect(URL, page, java.time.Instant.now()).product()).isNull();
        assertThat(OfficialProductParser.inspect(URL, page, java.time.Instant.now(), true).product().identity()).isEqualTo("bose-kr:307");
    }
}
