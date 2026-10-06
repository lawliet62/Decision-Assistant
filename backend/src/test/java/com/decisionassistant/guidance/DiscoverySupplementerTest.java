package com.decisionassistant.guidance;

import com.decisionassistant.lookup.*;
import com.decisionassistant.template.IntakeTemplates;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.guidance.OfficialSpecificationTest.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DiscoverySupplementerTest {
    final ProductLookupService search = mock(ProductLookupService.class);
    final WebEvidenceProvider web = mock(WebEvidenceProvider.class);
    final DiscoverySupplementer supplementer = new DiscoverySupplementer(search, web, new PublicUrlPolicy());
    final UUID owner = UUID.randomUUID();
    final IntakeTemplates.IntakeTemplate template = new IntakeTemplates().required(EARBUDS);
    DiscoveryRequest request(Boolean consent) {
        return new DiscoveryRequest("intake-v2", null, List.of("출퇴근"), null, "Android", true, true, true, true, Map.of(), consent);
    }
    SourcePage page(String model, int price) {
        var p = product(model, PURCHASE + price, "qcy:" + model + ":" + price, price);
        return new SourcePage(p.offer().purchaseUrl(), p.name(), p.name(), NOW, p);
    }
    @Test void onlyTwoDistinctMissingModelsUseOneSearchAndOneDocumentEach() {
        when(search.lookupForOwner(anyString(), eq(owner))).thenAnswer(call -> {
            var query = (String)call.getArgument(0);
            var token = query.contains("T13 ANC") ? "t13-anc" : "ht16";
            return List.of(new WebSearchProvider.SearchResult("미검증 검색 제목", "https://www.qcy.com/products/" + token, "ignored"));
        });
        when(web.scrapeProduct(anyString())).thenAnswer(call -> {
            var url = (String)call.getArgument(0);
            return new WebEvidenceProvider.Page("", "", html(url.endsWith("t13-anc") ? "QCY T13 ANC" : "QCY HT16", "<dl><dt>Compatible system</dt><dd>Android/iOS</dd></dl>"), url);
        });
        var result = supplementer.collect(UUID.randomUUID(), owner, request(true), template,
                List.of(page("HT19", 50000), page("T13 ANC", 29900), page("T13 ANC", 30000), page("HT16", 40000)));
        assertThat(result.factsByPurchaseUrl()).hasSize(2);
        assertThat(result.failures()).isEmpty();
        verify(search, times(2)).lookupForOwner(anyString(), eq(owner));
        verify(web, times(2)).scrapeProduct(anyString());
        verify(search, never()).lookupForOwner(contains("HT19"), any());
        verify(search, never()).lookupForOwner(contains("출퇴근"), any());
    }
    @Test void legacyConsentNoMissingConditionsAndUnknownModelAvoidAdditionalCalls() {
        for (var consent : Arrays.asList(null, false)) {
            supplementer.collect(UUID.randomUUID(), owner, request(consent), template, List.of(page("T13 ANC", 29900)));
            assertThat(OfficialStorePolicy.version(request(consent))).isEqualTo("official-kr-v2");
        }
        supplementer.collect(UUID.randomUUID(), owner, new DiscoveryRequest("intake-v2", null, List.of(), null, null,
                true, true, true, true, Map.of(), true), template, List.of(page("T13 ANC", 29900)));
        supplementer.collect(UUID.randomUUID(), owner, request(true), template, List.of(page("Unknown", 29900)));
        verifyNoInteractions(search, web);
    }
    @Test void knownRequiredEvidenceAvoidsSpendingAndFailureDoesNotLoseOtherResults() {
        var known = page("T13 ANC", 29900);
        var model = known.officialProduct();
        known = new SourcePage(known.url(), known.title(), known.excerpt(), NOW, model,
                List.of(new EvidenceBlock("b0", model.identity(), "호환성", "호환성", "Android, iOS", "호환성: Android, iOS")));
        supplementer.collect(UUID.randomUUID(), owner, request(true), template, List.of(known));
        verifyNoInteractions(search, web);
        when(search.lookupForOwner(anyString(), any())).thenThrow(new LookupException(429, "SEARCH_MONTHLY_LIMIT", "private provider message"));
        var result = supplementer.collect(UUID.randomUUID(), owner, request(true), template, List.of(page("HT16", 40000), page("HT19", 50000)));
        assertThat(result.failures()).extracting(DiscoveryFailure::code).containsExactly(DiscoveryFailure.Code.SEARCH_MONTHLY_LIMIT);
        verify(search, times(1)).lookupForOwner(anyString(), eq(owner));
        verifyNoInteractions(web);
    }
    @Test void searchTitleAndSnippetDoNotBypassOfficialHostAndModelValidation() {
        when(search.lookupForOwner(anyString(), any())).thenReturn(List.of(new WebSearchProvider.SearchResult("QCY T13 ANC", "https://example.com/spec", "Android supported")));
        var result = supplementer.collect(UUID.randomUUID(), owner, request(true), template, List.of(page("T13 ANC", 29900)));
        assertThat(result.factsByPurchaseUrl()).isEmpty();
        verifyNoInteractions(web);
    }
}
