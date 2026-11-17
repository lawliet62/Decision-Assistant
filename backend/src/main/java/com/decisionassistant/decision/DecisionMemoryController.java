package com.decisionassistant.decision;

import com.decisionassistant.identity.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.decisionassistant.decision.DecisionDtos.*;

@RestController
@RequestMapping("/api/v1/decisions/{decisionId}/memories")
public class DecisionMemoryController {
    public record Memory(UUID decisionId, String title, UUID resolutionId, DecisionTypes.ResolutionKind kind,
                         String selectedName, String rationale, List<String> reasonIds, String primaryReasonId,
                         java.time.Instant decidedAt, List<OutcomeData> outcomes, Resolution.PurchaseStatus purchaseStatus,
                         ContextData context, Comparison.Settings conditions) {}
    private final EntityManager em;
    private final DecisionService service;
    private final CurrentUserProvider user;
    public DecisionMemoryController(EntityManager em, DecisionService service, CurrentUserProvider user) {
        this.em = em; this.service = service; this.user = user;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public List<Memory> list(@PathVariable UUID decisionId) {
        var current = service.required(decisionId);
        var rows = em.createQuery("""
            select r from Resolution r, Decision d where r.decisionId = d.id and d.ownerUserId = :owner
            and d.templateKey = :category and d.id <> :id and d.deletedAt is null and d.personalizationEnabled = true
            and (d.editMode is null or d.editMode <> :correction)
            and r.supersededAt is null order by r.createdAt desc, r.id
            """, Resolution.class).setParameter("owner", user.current().id()).setParameter("category", current.templateKey())
            .setParameter("id", decisionId).setParameter("correction", DecisionTypes.EditMode.CORRECTION).setMaxResults(3).getResultList();
        return rows.stream().map(r -> {
            var d = em.find(Decision.class, r.decisionId());
            var selected = r.comparisonSnapshot() == null ? null : r.comparisonSnapshot().options().stream()
                    .filter(o -> o.id().equals(r.selectedOptionId())).map(OptionData::name).findFirst().orElse(null);
            var outcomes = em.createQuery("select o from OutcomeCheckIn o where o.decisionId = :id and o.resolutionId = :resolution order by o.createdAt desc", OutcomeCheckIn.class)
                    .setParameter("id", d.id()).setParameter("resolution", r.id()).setMaxResults(2).getResultList().stream()
                    .map(o -> new OutcomeData(o.id(), o.satisfaction(), o.expectationStatus(), o.notes(), o.wouldChooseAgain(), o.workedWell(), o.disappointedBy(), o.createdAt(), o.resolutionId())).toList();
            var snapshot = r.comparisonSnapshot();
            var context = snapshot == null ? null : snapshot.context();
            return new Memory(d.id(), snapshot == null ? d.title() : snapshot.title(), r.id(), r.kind(), selected, r.rationale(), r.reasonIds(), r.primaryReasonId(), r.createdAt(), outcomes, r.purchaseStatus(),
                    context == null ? null : new ContextData(context.intendedUse(), context.budget(), context.currency(), null),
                    snapshot == null || snapshot.comparison() == null ? null : snapshot.comparison().settings());
        }).toList();
    }
}
