package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AnkerLibertySpecificationVerifierTest {
    private static final String PAGE = """
            <title>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio</title>
            <link rel="canonical" href="https://www.soundcore.com/products/a3957-liberty-5-tws-earbuds">
            <h1>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio</h1>
            <meta name="description" content="Buy soundcore Liberty 5 earbuds with Dolby 3D Audio, adaptive ANC, and premium sound.">
            <strong>Real-Time Adaptive ANC 3.0:</strong>
            <div><h2>Specs</h2><table><tbody><tr><td>Multipoint Connection</td><td>Yes</td></tr></tbody></table></div>
            """;
    @Test void matchesOnlyTheExactModelsSingleSpecificationRow() {
        assertThat(AnkerLibertySpecificationVerifier.matchesMultipoint(PAGE)).isTrue();
        for (var html : new String[]{PAGE.replace("a3957", "a3954"), PAGE.replace("Liberty 5", "Liberty 4"),
                PAGE.replace(">Yes<", ">No<"), PAGE.replace(">Yes<", ">Yes, depending on device<"),
                PAGE.replace("Specs", "Other products"), PAGE + "<table><tr><td>Other model</td></tr></table>",
                PAGE.replace("</tbody>", "<tr><td>Multipoint Connection</td><td>No</td></tr></tbody>"),
                PAGE.replace("Multipoint Connection", "Bluetooth Version") + "<aside>Multipoint Connection Yes</aside>"}) {
            assertThat(AnkerLibertySpecificationVerifier.matchesMultipoint(html)).isFalse();
        }
    }
    @Test void ancAndMultipointAreVerifiedIndependently() {
        var current = PAGE.replace("<title>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio</title>",
                "<title>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio</title>");
        assertThat(AnkerLibertySpecificationVerifier.matches(current)).isTrue();
        assertThat(AnkerLibertySpecificationVerifier.matchesMultipoint(current)).isTrue();
        assertThat(AnkerLibertySpecificationVerifier.matches(current + "<title>Other</title>")).isFalse();
        assertThat(AnkerLibertySpecificationVerifier.matches(PAGE.replace(">Yes<", ">No<"))).isTrue();
        assertThat(AnkerLibertySpecificationVerifier.matchesMultipoint(PAGE.replace("Real-Time Adaptive ANC 3.0:", "No ANC"))).isTrue();
    }
}
