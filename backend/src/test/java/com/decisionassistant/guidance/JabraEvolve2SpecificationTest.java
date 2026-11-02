package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JabraEvolve2SpecificationTest {
    private static final String TITLE = "Jabra unveils professional, pocketable true wireless earbuds engineered for hybrid and remote working";
    private static final String HTML = """
            <title>%s</title><link rel='canonical' href='%s'>
            <div><h1>%s</h1><ul><li>Launches new Evolve2 Buds</li>
            <li><strong>Adjustable Active Noise Cancellation</strong> - filters out background noise allowing for increased focus</li></ul></div>
            """.formatted(TITLE, JabraEvolve2SpecificationSource.URL, TITLE);

    @Test void confirmsOnlyTheDesignatedModelAndExplicitAnc() {
        assertThat(JabraEvolve2SpecificationRefreshWorker.matches(HTML)).isTrue();
        assertThat(JabraEvolve2SpecificationRefreshWorker.matches(HTML.replace("Launches new Evolve2 Buds", "Launches other earbuds"))).isFalse();
        assertThat(JabraEvolve2SpecificationRefreshWorker.matches(HTML.replace("Active Noise Cancellation", "Passive Noise Isolation"))).isFalse();
        assertThat(JabraEvolve2SpecificationRefreshWorker.matches(HTML.replace("filters out background noise", "unavailable"))).isFalse();
        assertThat(JabraEvolve2SpecificationRefreshWorker.matches(HTML.replace("2022-jabra-evolve2-buds", "other-product"))).isFalse();
    }
}
