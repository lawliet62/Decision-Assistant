package com.decisionassistant.decision;

import com.decisionassistant.common.ConflictException;
import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.template.DecisionTemplateFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.decisionassistant.decision.DecisionDtos.*;

@Service
public class DecisionService {

    private final DecisionRepository decisions;
    private final ResolutionRepository resolutions;
    private final OutcomeCheckInRepository outcomes;
    private final DecisionTemplateFactory templates;
    private final Clock clock;

    public DecisionService(DecisionRepository decisions, ResolutionRepository resolutions,
                           OutcomeCheckInRepository outcomes, DecisionTemplateFactory templates) {
        this.decisions = decisions;
        this.resolutions = resolutions;
        this.outcomes = outcomes;
        this.templates = templates;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public DecisionResponse create(CreateDecisionRequest request) {
        var now = Instant.now(clock);
        var decision = new Decision(request.title(), request.templateKey(), templates.version(request.templateKey()), now);
        templates.apply(decision);
        decisions.saveAndFlush(decision);
        return response(decision);
    }

    @Transactional(readOnly = true)
    public List<DecisionSummary> list() {
        return decisions.findAllByOrderByUpdatedAtDesc().stream()
                .map(decision -> new DecisionSummary(
                        decision.id(), decision.version(), decision.title(), decision.templateKey(),
                        decision.status(), decision.updatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DecisionResponse get(UUID id) {
        return response(required(id));
    }

    @Transactional
    public DecisionResponse update(UUID id, UpdateDraftRequest request) {
        var decision = required(id);
        if (decision.version() != request.expectedVersion()) {
            throw new ConflictException("Decision version이 변경되었습니다. 최신 draft를 다시 불러오세요.");
        }
        if (decision.status() != DecisionTypes.DecisionStatus.DRAFT
                && decision.status() != DecisionTypes.DecisionStatus.DEFERRED) {
            throw new ConflictException("선택 또는 포기된 Decision은 draft를 수정할 수 없습니다.");
        }

        decision.updateDraft(request.title(), new DecisionContext(
                request.context().intendedUse(), request.context().budget(),
                normalizeCurrency(request.context().currency()), request.context().notes()), Instant.now(clock));

        syncOptions(decision, request.options());
        syncCriteria(decision, request.criteria());
        syncConstraints(decision, request.constraints());
        validateReferences(decision, request.values(), request.constraintResults());
        syncValues(decision, request.values());
        syncConstraintResults(decision, request.constraintResults());
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public ResolutionData resolve(UUID id, CreateResolutionRequest request) {
        var decision = required(id);
        if (request.kind() == DecisionTypes.ResolutionKind.CHOSEN) {
            if (request.selectedOptionId() == null
                    || decision.options().stream().noneMatch(option -> option.id().equals(request.selectedOptionId()))) {
                throw new InvalidDecisionException(List.of("CHOSEN resolution에는 Decision의 selectedOptionId가 필요합니다."));
            }
        } else if (request.selectedOptionId() != null) {
            throw new InvalidDecisionException(List.of("DEFERRED/ABANDONED resolution에는 selectedOptionId를 지정하지 않습니다."));
        }
        var now = Instant.now(clock);
        var resolution = resolutions.save(new Resolution(
                id, request.kind(), request.selectedOptionId(), request.rationale(), now));
        decision.resolve(switch (request.kind()) {
            case CHOSEN -> DecisionTypes.DecisionStatus.CHOSEN;
            case DEFERRED -> DecisionTypes.DecisionStatus.DEFERRED;
            case ABANDONED -> DecisionTypes.DecisionStatus.ABANDONED;
        }, now);
        return resolutionData(resolution);
    }

    @Transactional
    public OutcomeData addOutcome(UUID id, CreateOutcomeRequest request) {
        var decision = required(id);
        if (decision.status() != DecisionTypes.DecisionStatus.CHOSEN) {
            throw new ConflictException("Outcome은 실제 Option을 선택한 Decision에만 기록할 수 있습니다.");
        }
        var outcome = outcomes.save(new OutcomeCheckIn(
                id, request.satisfaction(), request.expectationStatus(), request.notes(), Instant.now(clock)));
        return outcomeData(outcome);
    }

    @Transactional(readOnly = true)
    public Decision required(UUID id) {
        return decisions.findById(id).orElseThrow(() -> new NotFoundException("Decision을 찾을 수 없습니다: " + id));
    }

    private void syncOptions(Decision decision, List<OptionData> inputs) {
        var ids = normalizedIds(inputs, OptionData::id);
        var existing = decision.mutableOptions().stream().collect(Collectors.toMap(DecisionOption::id, Function.identity()));
        decision.mutableOptions().removeIf(item -> !ids.contains(item.id()));
        inputs.forEach(input -> {
            var id = input.id() == null ? UUID.randomUUID() : input.id();
            var item = existing.get(id);
            if (item == null) {
                decision.addOption(id, input.name(), input.description(), input.sourceUrl(), input.sourceText());
            } else {
                item.update(input.name(), input.description(), input.sourceUrl(), input.sourceText());
            }
        });
    }

    private void syncCriteria(Decision decision, List<CriterionData> inputs) {
        var ids = normalizedIds(inputs, CriterionData::id);
        var existing = decision.mutableCriteria().stream().collect(Collectors.toMap(Criterion::id, Function.identity()));
        decision.mutableCriteria().removeIf(item -> !ids.contains(item.id()));
        inputs.forEach(input -> {
            var id = input.id() == null ? UUID.randomUUID() : input.id();
            var item = existing.get(id);
            if (item == null) {
                decision.addCriterion(id, input.name(), input.kind(), input.direction(), input.weight(),
                        input.worstAnchor(), input.bestAnchor(), input.unit(), input.active());
            } else {
                item.update(input.name(), input.kind(), input.direction(), input.weight(),
                        input.worstAnchor(), input.bestAnchor(), input.unit(), input.active());
            }
        });
    }

    private void syncConstraints(Decision decision, List<ConstraintData> inputs) {
        var ids = normalizedIds(inputs, ConstraintData::id);
        var existing = decision.mutableConstraints().stream()
                .collect(Collectors.toMap(DecisionConstraint::id, Function.identity()));
        decision.mutableConstraints().removeIf(item -> !ids.contains(item.id()));
        inputs.forEach(input -> {
            var id = input.id() == null ? UUID.randomUUID() : input.id();
            var item = existing.get(id);
            if (item == null) {
                decision.addConstraint(id, input.name(), input.kind(), input.criterionId(), input.threshold(),
                        input.unit(), input.description(), input.active());
            } else {
                item.update(input.name(), input.kind(), input.criterionId(), input.threshold(),
                        input.unit(), input.description(), input.active());
            }
        });
    }

    private void syncValues(Decision decision, List<OptionValueData> inputs) {
        var ids = normalizedIds(inputs, OptionValueData::id);
        var existing = decision.mutableValues().stream().collect(Collectors.toMap(OptionValue::id, Function.identity()));
        decision.mutableValues().removeIf(item -> !ids.contains(item.id()));
        inputs.forEach(input -> {
            var id = input.id() == null ? UUID.randomUUID() : input.id();
            var item = existing.get(id);
            if (item == null) {
                decision.addValue(id, input.optionId(), input.criterionId(), input.numericValue(), input.rating(),
                        input.certainty(), input.sourceUrl(), input.sourceNote());
            } else {
                item.update(input.optionId(), input.criterionId(), input.numericValue(), input.rating(),
                        input.certainty(), input.sourceUrl(), input.sourceNote());
            }
        });
    }

    private void syncConstraintResults(Decision decision, List<ConstraintResultData> inputs) {
        var ids = normalizedIds(inputs, ConstraintResultData::id);
        var existing = decision.mutableConstraintResults().stream()
                .collect(Collectors.toMap(ConstraintResult::id, Function.identity()));
        decision.mutableConstraintResults().removeIf(item -> !ids.contains(item.id()));
        inputs.forEach(input -> {
            var id = input.id() == null ? UUID.randomUUID() : input.id();
            var item = existing.get(id);
            if (item == null) {
                decision.addConstraintResult(id, input.optionId(), input.constraintId(), input.status(), input.note());
            } else {
                item.update(input.optionId(), input.constraintId(), input.status(), input.note());
            }
        });
    }

    private void validateReferences(Decision decision, List<OptionValueData> values,
                                    List<ConstraintResultData> results) {
        var optionIds = decision.options().stream().map(DecisionOption::id).collect(Collectors.toSet());
        var criterionIds = decision.criteria().stream().map(Criterion::id).collect(Collectors.toSet());
        var constraintIds = decision.constraints().stream().map(DecisionConstraint::id).collect(Collectors.toSet());
        var violations = new java.util.ArrayList<String>();
        var valueKeys = new HashSet<String>();
        values.forEach(value -> {
            if (!optionIds.contains(value.optionId()) || !criterionIds.contains(value.criterionId())) {
                violations.add("OptionValue가 현재 Option/Criterion을 참조해야 합니다.");
            }
            if (!valueKeys.add(value.optionId() + ":" + value.criterionId())) {
                violations.add("같은 Option과 Criterion에는 값이 하나만 존재할 수 있습니다.");
            }
        });
        var resultKeys = new HashSet<String>();
        results.forEach(result -> {
            if (!optionIds.contains(result.optionId()) || !constraintIds.contains(result.constraintId())) {
                violations.add("ConstraintResult가 현재 Option/Constraint를 참조해야 합니다.");
            }
            if (!resultKeys.add(result.optionId() + ":" + result.constraintId())) {
                violations.add("같은 Option과 Constraint에는 결과가 하나만 존재할 수 있습니다.");
            }
        });
        if (!violations.isEmpty()) {
            throw new InvalidDecisionException(violations);
        }
    }

    private <T> Set<UUID> normalizedIds(List<T> inputs, Function<T, UUID> extractor) {
        var ids = inputs.stream().map(extractor).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        var nonNullCount = inputs.stream().map(extractor).filter(java.util.Objects::nonNull).count();
        if (ids.size() != nonNullCount) {
            throw new InvalidDecisionException(List.of("중복된 child id가 있습니다."));
        }
        return ids;
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? null : currency.toUpperCase(java.util.Locale.ROOT);
    }

    private DecisionResponse response(Decision decision) {
        return new DecisionResponse(
                decision.id(), decision.version(), decision.title(), decision.templateKey(), decision.templateVersion(),
                decision.status(), new ContextData(decision.context().intendedUse(), decision.context().budget(),
                decision.context().currency(), decision.context().notes()),
                decision.options().stream().map(option -> new OptionData(option.id(), option.name(), option.description(),
                        option.sourceUrl(), option.sourceText())).toList(),
                decision.criteria().stream().map(criterion -> new CriterionData(
                        criterion.id(), criterion.name(), criterion.kind(), criterion.direction(), criterion.weight(),
                        criterion.worstAnchor(), criterion.bestAnchor(), criterion.unit(), criterion.active())).toList(),
                decision.values().stream().map(value -> new OptionValueData(
                        value.id(), value.optionId(), value.criterionId(), value.numericValue(), value.rating(),
                        value.certainty(), value.sourceUrl(), value.sourceNote())).toList(),
                decision.constraints().stream().map(constraint -> new ConstraintData(
                        constraint.id(), constraint.name(), constraint.kind(), constraint.criterionId(),
                        constraint.threshold(), constraint.unit(), constraint.description(), constraint.active())).toList(),
                decision.constraintResults().stream().map(result -> new ConstraintResultData(
                        result.id(), result.optionId(), result.constraintId(), result.status(), result.note())).toList(),
                resolutions.findByDecisionIdOrderByCreatedAtDesc(decision.id()).stream().map(this::resolutionData).toList(),
                outcomes.findByDecisionIdOrderByCreatedAtDesc(decision.id()).stream().map(this::outcomeData).toList(),
                decision.createdAt(), decision.updatedAt());
    }

    private ResolutionData resolutionData(Resolution resolution) {
        return new ResolutionData(resolution.id(), resolution.kind(), resolution.selectedOptionId(),
                resolution.rationale(), resolution.createdAt());
    }

    private OutcomeData outcomeData(OutcomeCheckIn outcome) {
        return new OutcomeData(outcome.id(), outcome.satisfaction(), outcome.expectationStatus(),
                outcome.notes(), outcome.createdAt());
    }
}
