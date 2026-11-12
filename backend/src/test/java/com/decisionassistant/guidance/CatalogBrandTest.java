package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.decisionassistant.guidance.CatalogProduct.BrandStatus.*;

class CatalogBrandTest {
    @Test void validStatesSeparateNameAbsenceAndPendingVerification() {
        assertThat(CatalogBrand.validate(" Anker ", KNOWN, "anker-buds", "soundcore", "anker:1")).isEqualTo("Anker");
        assertThat(CatalogBrand.validate(null, UNBRANDED, "generic", "이어폰", "generic:1")).isNull();
        assertThat(CatalogBrand.validate(null, UNVERIFIED, "generic", "이어폰", "generic:1")).isNull();
        // 브랜드 존재를 알아도 이름이 조사 중일 수 있다. 미확인을 오류나 브랜드 없음으로 바꾸지 않는다.
        assertThat(CatalogBrand.validate(null, UNVERIFIED, "qcy-test", "이어폰", "qcy:1")).isNull();
        assertThat(CatalogBrand.validate("노브랜드", KNOWN, "generic", "제품", "generic:1")).isEqualTo("노브랜드");
    }
    @Test void inconsistentNameAndStatusAreRejected() {
        for (var brand : new String[] {null, "", " \t\n"}) {
            assertThatThrownBy(() -> CatalogBrand.validate(brand, KNOWN, "generic", "이어폰", "generic:1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (var status : List.of(UNBRANDED, UNVERIFIED)) for (var brand : List.of("Anker", "", " ")) {
            assertThatThrownBy(() -> CatalogBrand.validate(brand, status, "generic", "이어폰", "generic:1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> CatalogBrand.validate("QCY", null, "generic", "제품", "generic:1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void knownMarkersOnlyRejectUnbrandedClaims() {
        for (var source : List.of("qcy-test", "AirPods Pro", "앤커 사운드코어", "audio-technica-kr:1", "BOSE", "마샬", "젠하이저", "소니", "삼성", "LG")) {
            for (int field = 0; field < 3; field++) {
                var id = field == 0 ? source : "generic";
                var name = field == 1 ? source : "이어폰";
                var identity = field == 2 ? source : "generic:1";
                assertThatThrownBy(() -> CatalogBrand.validate(null, UNBRANDED, id, name, identity))
                        .as(source).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
    @Test void statusChangesRequireRevision() {
        var p = new CatalogProduct("generic-buds");
        p.register(1, "이어폰", "https://example.com/buds", "generic:1", List.of(), true, null, UNVERIFIED);
        assertThatThrownBy(() -> p.register(1, p.name(), p.purchaseUrl(), p.expectedIdentity(), p.facts(), true, null, UNBRANDED))
                .isInstanceOf(IllegalArgumentException.class);
        p.register(2, p.name(), p.purchaseUrl(), p.expectedIdentity(), p.facts(), true, null, UNBRANDED);
        assertThat(p.brandStatus()).isEqualTo(UNBRANDED);
        p.register(3, p.name(), p.purchaseUrl(), p.expectedIdentity(), p.facts(), true, "노브랜드", KNOWN);
        assertThat(p.brandName()).isEqualTo("노브랜드");
    }
    @Test void importerRequiresExplicitConsistentStates() {
        var url = "https://qcystore.co.kr/product/buds/1000/";
        for (var status : List.of(UNBRANDED, UNVERIFIED))
            CatalogImporter.validate(new CatalogImporter.Entry("generic", 1, "이어폰", url, "generic:1", List.of(), true, null, status), java.time.Instant.now());
        assertThatThrownBy(() -> CatalogImporter.validate(new CatalogImporter.Entry("generic", 1, "이어폰", url,
                "generic:1", List.of(), true, null, KNOWN), java.time.Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
