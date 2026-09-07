package com.decisionassistant.lookup;

import com.decisionassistant.guidance.*;
import com.decisionassistant.identity.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductLookupServiceTest {
    @Test void validatesDeduplicatesAndReservesBeforeSearch() {
        var provider = mock(WebSearchProvider.class);
        var quota = mock(SearchQuota.class);
        var user = mock(CurrentUserProvider.class);
        when(user.current()).thenReturn(new CurrentUser(UUID.randomUUID(), "owner@example.com", "Owner"));
        when(provider.search("Buds", 5)).thenReturn(List.of(
                new WebSearchProvider.SearchResult("Buds", "https://example.com/buds", "text"),
                new WebSearchProvider.SearchResult("Again", "https://example.com/buds", "text"),
                new WebSearchProvider.SearchResult("Private", "http://[::1]/", "text")));
        var service = new ProductLookupService(provider, new SearchProperties(true, "test", 1000), quota,
                user, new PublicUrlPolicy(), new SimpleMeterRegistry());
        assertThat(service.lookup("Buds")).hasSize(1);
        var order = inOrder(quota, provider);
        order.verify(quota).reserve();
        order.verify(provider).search("Buds", 5);
        doThrow(new LookupException(429, "SEARCH_MONTHLY_LIMIT", "limit")).when(quota).reserve();
        assertThatThrownBy(() -> service.lookup("Buds")).isInstanceOf(LookupException.class);
        verify(provider, times(1)).search(anyString(), anyInt());
    }

    @Test void burstLimitExpiresAndIsPerUser() {
        var service = new ProductLookupService(null, null, null, null, null, new SimpleMeterRegistry());
        var user = UUID.randomUUID();
        var now = Instant.parse("2026-09-01T00:00:00Z");
        for (int i = 0; i < 5; i++) service.admit(user, now);
        assertThatThrownBy(() -> service.admit(user, now.plusSeconds(59))).isInstanceOf(LookupException.class);
        service.admit(UUID.randomUUID(), now);
        service.admit(user, now.plusSeconds(60));
    }
}
