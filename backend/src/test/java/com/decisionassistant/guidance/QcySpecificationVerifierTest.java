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
}
