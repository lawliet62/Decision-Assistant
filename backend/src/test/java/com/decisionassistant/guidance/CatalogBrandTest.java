package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CatalogBrandTest {
    @Test void unbrandedProductsAcceptExplicitBlankButNotMissingField() {
        assertThat(CatalogBrand.validate(" \t ", "generic-buds", "무선 이어폰", "generic:1")).isEmpty();
        assertThatThrownBy(() -> CatalogBrand.validate(null, "generic-buds", "무선 이어폰", "generic:1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void knownBrandCannotBeOmittedFromIdNameOrIdentity() {
        for (var source : List.of("qcy-test", "AirPods Pro", "앤커 사운드코어", "audio-technica-kr:1", "BOSE", "마샬", "젠하이저", "소니", "삼성", "LG")) {
            for (int field = 0; field < 3; field++) {
                var id = field == 0 ? source : "generic";
                var name = field == 1 ? source : "이어폰";
                var identity = field == 2 ? source : "generic:1";
                assertThatThrownBy(() -> CatalogBrand.validate(" \t", id, name, identity))
                        .as(source).isInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThat(CatalogBrand.validate("", "generic", "boseman", "generic:1")).isEmpty();
    }
    @Test void brandIsPersistedAndChangesRequireRevision() {
        var p = new CatalogProduct("anker-buds");
        p.register(1, "soundcore", "https://example.com/buds", "anker:1", List.of(), true, " Anker ");
        assertThat(p.brandName()).isEqualTo("Anker");
        assertThatThrownBy(() -> p.register(1, p.name(), p.purchaseUrl(), p.expectedIdentity(), p.facts(), true, "Other"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.register(2, p.name(), p.purchaseUrl(), p.expectedIdentity(), p.facts(), true, ""))
                .isInstanceOf(IllegalArgumentException.class);
        p.register(2, p.name(), p.purchaseUrl(), p.expectedIdentity(), p.facts(), true, "Other");
        assertThat(p.brandName()).isEqualTo("Other");
    }
    @Test void manifestRequiresExplicitFieldAndChecksKnownBrands() {
        var url = "https://qcystore.co.kr/product/buds/1000/";
        CatalogImporter.validate(new CatalogImporter.Entry("generic", 1, "이어폰", url, "generic:1", List.of(), true, ""), java.time.Instant.now());
        for (var brand : new String[] {null, "", " \t"}) {
            assertThatThrownBy(() -> CatalogImporter.validate(new CatalogImporter.Entry("qcy-buds", 1, "이어폰", url,
                    "qcy:1", List.of(), true, brand), java.time.Instant.now())).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
