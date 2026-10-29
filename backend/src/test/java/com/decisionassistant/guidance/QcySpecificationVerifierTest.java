package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QcySpecificationVerifierTest {
    private static final String PAGE = """
            <div class="specification-content"><div class="spec-details">
              <p>Product Name: QCY T13 Pro</p>
              <p>Type: In-Ear<br>Compatible Systems: iOS / Android<br>Water Resistance Range: IPX4</p>
            </div></div>
            """;

    @Test void verifiesOnlyTheMatchingModelsUnambiguousSpecification() {
        assertThat(QcySpecificationVerifier.matches(PAGE, "QCY T13 Pro", "Water Resistance Range", "IPX4")).isTrue();
        assertThat(QcySpecificationVerifier.matches(PAGE, "QCY T13 ANC", "Water Resistance Range", "IPX4")).isFalse();
        assertThat(QcySpecificationVerifier.matches(PAGE.replace("IPX4", "IPX5"), "QCY T13 Pro", "Water Resistance Range", "IPX4")).isFalse();
        assertThat(QcySpecificationVerifier.matches(PAGE.replace("IPX4", "IPX4<br>Water Resistance Range: IPX5"), "QCY T13 Pro", "Water Resistance Range", "IPX4")).isFalse();
        assertThat(QcySpecificationVerifier.matches(PAGE.replace("IPX4", "IPX5") + "<aside>Water Resistance Range: IPX4</aside>", "QCY T13 Pro", "Water Resistance Range", "IPX4")).isFalse();
    }
    @Test void ancRequiresTheExactModelAndItsDescription() {
        var page = "<title>QCY T13 Pro – QCY Official</title><meta name='description' content='50dB Adaptive ANC Up to 47H Playtime'>" + PAGE;
        assertThat(QcySpecificationVerifier.matchesAnc(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("QCY T13 Pro", "QCY T13 ANC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("50dB Adaptive ANC", "No ANC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page + "<meta name='description' content='No ANC'>")).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("Playtime", "Playtime without ANC"))).isFalse();
    }
    @Test void ht19RequiresModelMappingAndUnambiguousWaterResistance() {
        var page = "<title>QCY MeloBuds N60: Affordable Wireless Earbuds with Rich Sound – QCY Official</title>"
                + "<p class='product__text subtitle'>Bluetooth 6.0, 56dB ANC, Hi-Res, 45-Hour Playtime, HT19</p>"
                + PAGE.replace("QCY T13 Pro", "QCY MeloBuds N60").replace("IPX4", "IPX5");
        assertThat(QcySpecificationVerifier.matchesHt19WaterResistance(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesHt19WaterResistance(page.replace("HT19", "HT16"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19WaterResistance(page.replace("IPX5", "IPX4"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19WaterResistance(page + "<p class='product__text subtitle'>HT19</p>")).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19WaterResistance(page.replace("IPX5", "IPX5<br>Water Resistance Range: IPX4"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19ModelAndAnc(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesHt19ModelAndAnc(page.replace("56dB ANC", "No ANC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19ModelAndAnc(page.replace("Product Name: QCY MeloBuds N60", "Product Name: another model"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19ModelAndAnc(page.replace("IPX5", "IPX4"))).isTrue();
    }
    @Test void ht16AncRequiresExactProductAndExplicitTitle() {
        var name = "QCY HT16 멜로버즈 무선 블루투스 이어폰 ANC 노이즈캔슬링 블랙";
        var page = "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/990/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_990\"}</script>";
        assertThat(QcySpecificationVerifier.matchesHt16Anc(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesHt16Anc(page.replace("ANC 노이즈캔슬링", "일반 이어폰"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt16Anc(page.replace("cafe24_ylintlshop_1_990", "cafe24_ylintlshop_1_991"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt16Anc(page.replace("/990/", "/991/"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt16Anc(page + "<h1>다른 모델</h1>")).isFalse();
    }
}
