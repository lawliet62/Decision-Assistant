package com.decisionassistant.guidance;

import com.decisionassistant.ai.*;
import com.decisionassistant.common.*;
import com.decisionassistant.decision.Decision;
import com.decisionassistant.lookup.SearchProperties;
import com.decisionassistant.template.IntakeTemplates;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DiscoveryService {
    private final ProductDiscoveryRepository runs;
    private final IntakeTemplates templates;
    private final SearchProperties search;
    private final WebProperties web;
    private final AiProperties ai;
    private final AiDataPolicy policy;
    public DiscoveryService(ProductDiscoveryRepository runs, IntakeTemplates templates, SearchProperties search,
                            WebProperties web, AiProperties ai, AiDataPolicy policy) {
        this.runs = runs; this.templates = templates; this.search = search; this.web = web; this.ai = ai; this.policy = policy;
    }
    public IntakeTemplates.IntakeTemplate validate(com.decisionassistant.decision.DecisionTypes.TemplateKey key,
                                                  DiscoveryContracts.DiscoveryRequest request) {
        var template = templates.required(key);
        if (!template.version().equals(request.templateVersion())
                || request.selectedUses().size() > 4
                || request.selectedUses().stream().anyMatch(use -> use == null || !template.uses().contains(use))
                || (request.compatibility() != null && !template.compatibility().contains(request.compatibility()))) {
            throw new InvalidDecisionException(List.of("최신 템플릿의 선택 항목을 사용하세요."));
        }
        if (!Boolean.TRUE.equals(request.searchConsent()) || !Boolean.TRUE.equals(request.collectionConsent())
                || !Boolean.TRUE.equals(request.aiConsent()) || !Boolean.TRUE.equals(request.sensitiveDataAcknowledged())) {
            throw new InvalidDecisionException(List.of("검색·페이지 수집·AI 전송에 각각 동의해야 추천을 시작할 수 있습니다."));
        }
        policy.validateSensitiveDataAcknowledgement(Boolean.TRUE.equals(request.sensitiveDataAcknowledged()));
        if (!search.configured() || !web.configured() || !ai.enabled()) {
            throw new ConflictException("추천 서비스가 꺼져 있습니다. 제품명 또는 URL로 직접 추가하세요.");
        }
        return template;
    }
    public void start(Decision decision, DiscoveryContracts.DiscoveryRequest request) {
        runs.save(new ProductDiscovery(decision.id(), request));
    }
    public void retry(Decision decision, long expectedVersion) {
        var run = runs.findById(decision.id()).orElseThrow(() -> new NotFoundException("추천 요청이 없습니다."));
        if (run.version() != expectedVersion) throw new ConflictException("추천 상태가 변경되었습니다. 다시 불러오세요.");
        if (run.status() != ProductDiscovery.Status.FAILED && run.status() != ProductDiscovery.Status.PARTIAL) {
            throw new ConflictException("실패하거나 일부만 완료된 추천만 재시도할 수 있습니다.");
        }
        validate(decision.templateKey(), run.conditions());
        run.pending();
        runs.flush();
    }
}
