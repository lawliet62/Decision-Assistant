package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BoseQcSpecificationTest {
    private static final String HTML = """
            <link rel='canonical' href='%s'>
            <h1 class='product-name'>Bose QuietComfort Earbuds</h1>
            <input class='color-attribute' data-color='Ice Blue' id='iceblue-qcearb24-headphonein-ddpmnt-ww'>
            <div class='product-description__content'>The everyday, made epic. New Bose QuietComfort Earbuds offer iconic audio, renowned noise cancellation, and a battery.</div>
            <li class='product-carousel__item'>
              <a class='image-container-product-url-label' href='/p/earbuds/bose-quietcomfort-headphones/QCEARB24-HEADPHONEIN.html'>Bose QuietComfort Earbuds</a>
              <p class='product-attribute-name'>Active Noise Cancellation</p><p class='product-attribute-value'>Yes</p>
            </li>
            """.formatted(BoseQcSpecificationSource.URL);

    @Test void acceptsOnlyTheExpectedModelColorAndDescription() {
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML)).isTrue();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace("QCEARB24-HEADPHONEIN.html", "OTHER.html"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace("Bose QuietComfort Earbuds</h1>", "Bose Ultra Earbuds</h1>"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace("Ice Blue", "Black"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace("renowned noise cancellation", "sound"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace(">Yes</p>", ">No</p>"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace("Active Noise Cancellation", "Passive Isolation"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML.replace("QCEARB24-HEADPHONEIN.html'>Bose QuietComfort Earbuds</a>",
                "OTHER.html'>Bose QuietComfort Earbuds</a>"))).isFalse();
        assertThat(BoseQcSpecificationRefreshWorker.matches(HTML + "<div class='product-description__content'>Other</div>")).isFalse();
    }
}
