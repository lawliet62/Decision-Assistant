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
}
