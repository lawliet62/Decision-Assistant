package com.decisionassistant.guidance;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MarshallPriceDiagnosticsTest {
    private static final String URL = "https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450";
    private static final String REGULAR = "<p aria-label='정상 가격: ₩ 269,000'>₩ 269,000</p>";
    private static final String PURCHASE = "<div><p>₩ 269,000</p><button>₩ 269,000 - 장바구니</button></div>";

    @Test void distinguishesMissingAmbiguousMismatchedAndDisabledAreasWithoutLoggingPageContent() {
        var logger = (Logger) org.slf4j.LoggerFactory.getLogger(VisiblePrice.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            var pages = List.of(
                    PURCHASE,
                    REGULAR + REGULAR + PURCHASE,
                    REGULAR,
                    REGULAR + PURCHASE + PURCHASE,
                    REGULAR + PURCHASE.replace("<p>₩ 269,000", "<p>₩ 250,000"),
                    REGULAR + PURCHASE.replace("269,000 -", "250,000 -"),
                    REGULAR + PURCHASE.replace("<button>", "<button disabled>"),
                    REGULAR + PURCHASE.replace("<button>", "<button aria-disabled='true'>"));
            for (var html : pages) {
                var result = inspect(html + "<aside>PRIVATE_PAGE_SENTINEL</aside>");
                assertThat(result.rejection()).isEqualTo(OfficialProductParser.Rejection.PRICE_CONTEXT_MISSING);
            }
            assertThat(events.list.stream().map(ILoggingEvent::getFormattedMessage)).containsExactly(
                    message("REGULAR_PRICE_AREA", 0, 1), message("REGULAR_PRICE_AREA", 2, 1),
                    message("PURCHASE_AREA", 1, 0), message("PURCHASE_AREA", 1, 2),
                    message("PURCHASE_PRICE_MISMATCH", 1, 1), message("BUTTON_PRICE_OR_LABEL_MISMATCH", 1, 1),
                    message("BUTTON_DISABLED", 1, 1), message("BUTTON_DISABLED", 1, 1));
            assertThat(events.list.stream().map(ILoggingEvent::getFormattedMessage))
                    .allSatisfy(text -> assertThat(text).doesNotContain("PRIVATE_PAGE_SENTINEL", "269,000", "250,000", "<"));
            events.list.clear();
            assertThat(inspect(REGULAR + PURCHASE).rejection()).isNull();
            assertThat(events.list).isEmpty();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    private static VisiblePrice.Result inspect(String html) {
        return VisiblePrice.inspect(Jsoup.parse("<h1>MOTIF II A.N.C.</h1>" + html, URL), URL,
                "MOTIF II A.N.C.", "1006450", new BigDecimal("269000"));
    }

    private static String message(String stage, int regular, int purchase) {
        return "marshall_visible_price_check sku=1006450 stage=" + stage + " regularMatches=" + regular + " purchaseAreas=" + purchase;
    }
}
