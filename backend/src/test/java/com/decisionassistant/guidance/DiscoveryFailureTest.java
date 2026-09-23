package com.decisionassistant.guidance;

import com.decisionassistant.ai.AiInvocationException;
import com.decisionassistant.lookup.LookupException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.*;
import static com.decisionassistant.guidance.DiscoveryFailure.*;

class DiscoveryFailureTest {
    @Test void wrappedFailuresAreClassifiedByStageAndCauseInsteadOfMessage() {
        var conversion = new HttpMessageConversionException("fixture-private-response", new IllegalArgumentException("fixture-secret"));
        var ai = new AiInvocationException("provider returned fixture-secret", conversion);
        assertThat(classify(Stage.COLLECTION, conversion).code()).isEqualTo(Code.COLLECTION_RESPONSE_INVALID);
        assertThat(classify(Stage.AI, ai).code()).isEqualTo(Code.AI_RESPONSE_INVALID);
        assertThat(classify(Stage.AI, new AiInvocationException("timed out", new TimeoutException())).code()).isEqualTo(Code.AI_TIMEOUT);
        assertThat(classify(Stage.COLLECTION, new ResourceAccessException("url with secret", new SocketTimeoutException())).code()).isEqualTo(Code.COLLECTION_TIMEOUT);
        assertThat(classify(Stage.AI, new IllegalStateException("timeout JSON fixture-secret")).code()).isEqualTo(Code.AI_FAILED);
        assertThat(rootCauseType(ai)).isEqualTo("IllegalArgumentException");
        for (var stage : Stage.values()) assertThat(classify(stage, ai).message()).doesNotContain("fixture-secret", "fixture-private-response");
    }

    @Test void quotaAndBurstLimitsRemainDistinctAndHttpStatusIsSafeMetadata() {
        assertThat(classify(Stage.SEARCH, new LookupException(429, "SEARCH_MONTHLY_LIMIT", "private")).code()).isEqualTo(Code.SEARCH_MONTHLY_LIMIT);
        assertThat(classify(Stage.SEARCH, new LookupException(429, "SEARCH_RATE_LIMIT", "private")).code()).isEqualTo(Code.SEARCH_BUSY);
        assertThat(classify(Stage.SEARCH, new LookupException(504, "SEARCH_TIMEOUT", "private")).code()).isEqualTo(Code.SEARCH_TIMEOUT);
        var http = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "fixture-secret");
        assertThat(httpStatus(new IllegalStateException("wrapped", http))).isEqualTo(401);
        assertThat(classify(Stage.COLLECTION, http).message()).doesNotContain("fixture-secret");
    }
}
