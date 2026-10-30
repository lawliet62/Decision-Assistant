package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QcySpecificationVerifierTest {
    @Test void ct06RequiresExactProductAndOpenForm() {
        var name = "QCY CT06 오픈형 귀찌 무선 블루투스 이어폰 러닝 자전거 운동용 이어커프";
        var page = "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/994/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_994\"}</script>";
        assertThat(QcySpecificationVerifier.matchesCt06Form(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesCt06Form(page.replace("오픈형", "커널형"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesCt06Form(page.replace("994", "995"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesCt06Form(page + "<h1>다른 상품</h1>")).isFalse();
    }
    @Test void ht08AncRequiresExactArticleAndPositiveModelStatement() {
        var title = "QCY, 적응형 노이즈 캔슬링 블루투스 이어폰 ‘HT08 멜로버즈 프로 플러스’ 출시";
        var page = "<title>" + title + " 도매특판 - QCY스토어</title><form id='BoardDelForm'><input id='no' name='no' value='16170'>"
                + "<div id='gd' class='xans-board-read'><div class='titleArea'><h2>" + title + "</h2></div>"
                + "<div class='detail'><div class='fr-view-article'><p>QCY HT08 멜로버즈 프로 플</p>"
                + "<p>러스는 적응형 액티브 노이즈 캔슬링 기능으로 편안한 청취를 제공한다.</p></div></div></div></form>";
        assertThat(QcySpecificationVerifier.matchesHt08Anc(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesHt08Anc(page.replace("16170", "16171"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt08Anc(page.replace("HT08", "HT10"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt08Anc(page.replace("기능으로", "기능이 없으며"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt08Anc(page.replace("fr-view-article", "unrelated"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt08Anc(page.replace("</h2>", "</h2><h2>다른 기사</h2>"))).isFalse();
    }
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
        assertThat(QcySpecificationVerifier.matches(PAGE.replace("Product Name: QCY T13 Pro", "Product Name: QCY T13 Pro<br>Product Name: another model"), "QCY T13 Pro", "Water Resistance Range", "IPX4")).isFalse();
    }
    @Test void ancRequiresTheExactModelAndItsDescription() {
        var page = "<title>QCY T13 Pro – QCY Official</title><meta name='description' content='50dB Adaptive ANC Up to 47H Playtime'>" + PAGE;
        assertThat(QcySpecificationVerifier.matchesAnc(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("QCY T13 Pro", "QCY T13 ANC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("50dB Adaptive ANC", "No ANC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page + "<meta name='description' content='No ANC'>")).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("Playtime", "Playtime without ANC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesAnc(page.replace("Product Name: QCY T13 Pro", "Product Name: QCY T13 Pro<br>Product Name: another model"))).isFalse();
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
        var ambiguous = page.replace("Product Name: QCY MeloBuds N60", "Product Name: QCY MeloBuds N60<br>Product Name: another model");
        assertThat(QcySpecificationVerifier.matchesHt19ModelAndAnc(ambiguous)).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt19WaterResistance(ambiguous)).isFalse();
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
    @Test void t17AppFormRequiresExactProductAndExplicitTitle() {
        var name = "QCY T17APP 블루투스이어폰 커널형 무선이어폰 저지연 큐씨와이 초경량 블루투스5.3버전 블랙";
        var page = "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/563/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_563\"}</script>";
        assertThat(QcySpecificationVerifier.matchesT17AppForm(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesT17AppForm(page.replace("커널형", "오픈형"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesT17AppForm(page.replace("T17APP", "T17S"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesT17AppForm(page.replace("cafe24_ylintlshop_1_563", "cafe24_ylintlshop_1_564"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesT17AppForm(page.replace("/563/", "/564/"))).isFalse();
    }
    @Test void ht10AncRequiresExactProductAndExplicitTitle() {
        var name = "QCY HT10 Hi-Res ANC APP 노이즈캔슬링 블루투스 이어폰 LDAC / 블랙";
        var page = "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/924/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_924\"}</script>";
        assertThat(QcySpecificationVerifier.matchesHt10Anc(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesHt10Anc(page.replace("ANC", "ENC"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt10Anc(page.replace("HT10", "HT20"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt10Anc(page.replace("cafe24_ylintlshop_1_924", "cafe24_ylintlshop_1_925"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt10Anc(page.replace("/924/", "/925/"))).isFalse();
    }
    @Test void t29FormRequiresExactProductAndExplicitTitle() {
        var name = "QCY T29 APP 블루투스이어폰 오픈형 13mm 드라이버";
        var page = "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/709/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_709\"}</script>";
        assertThat(QcySpecificationVerifier.matchesT29Form(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesT29Form(page.replace("오픈형", "커널형"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesT29Form(page.replace("T29", "T28"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesT29Form(page.replace("cafe24_ylintlshop_1_709", "cafe24_ylintlshop_1_710"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesT29Form(page.replace("/709/", "/710/"))).isFalse();
    }
    @Test void ht20FormRequiresExactProductAndExplicitTitle() {
        var name = "QCY HT20 멜로버즈 블루투스 무선 이어폰 노이즈캔슬링 AI마이크 C타입 커널형 게이밍";
        var page = "<title>" + name + " - QCY스토어</title><h1>" + name + "</h1>"
                + "<link rel='canonical' href='https://qcystore.co.kr/product/model/1028/'>"
                + "<script type='application/ld+json'>{\"@type\":\"ProductGroup\",\"name\":\"" + name + "\",\"productGroupID\":\"cafe24_ylintlshop_1_1028\"}</script>";
        assertThat(QcySpecificationVerifier.matchesHt20Form(page)).isTrue();
        assertThat(QcySpecificationVerifier.matchesHt20Form(page.replace("커널형", "오픈형"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt20Form(page.replace("HT20", "HT19"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt20Form(page.replace("cafe24_ylintlshop_1_1028", "cafe24_ylintlshop_1_1029"))).isFalse();
        assertThat(QcySpecificationVerifier.matchesHt20Form(page.replace("/1028/", "/1029/"))).isFalse();
    }
}
