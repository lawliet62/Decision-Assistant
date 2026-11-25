package com.decisionassistant.decision;

import com.decisionassistant.guidance.OfficialStorePolicy;
import com.decisionassistant.guidance.DiscoveryRanking;

import com.decisionassistant.common.ConflictException;
import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.identity.CurrentUserProvider;
import com.decisionassistant.evaluation.ReadinessAnalyzer;
import com.decisionassistant.guidance.CandidateSuggestion;
import com.decisionassistant.guidance.CandidateSuggestionRepository;
import com.decisionassistant.guidance.DecisionQuestion;
import com.decisionassistant.guidance.DecisionQuestionRepository;
import com.decisionassistant.guidance.EvidenceImport;
import com.decisionassistant.guidance.EvidenceImportRepository;
import com.decisionassistant.guidance.PublicUrlPolicy;
import com.decisionassistant.guidance.WebProperties;
import com.decisionassistant.guidance.DiscoveryContracts;
import com.decisionassistant.guidance.MinimumRequirementMatcher;
import com.decisionassistant.guidance.ProductFactResolver;
import com.decisionassistant.template.MinimumRequirements;
import com.decisionassistant.notification.OutcomeReminder;
import com.decisionassistant.notification.OutcomeReminderRepository;
import com.decisionassistant.preference.PreferenceMemory;
import com.decisionassistant.preference.PreferenceMemoryRepository;
import com.decisionassistant.template.DecisionTemplateFactory;
import com.decisionassistant.ai.AiDataPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
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

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    private final DecisionRepository decisions;
    private final ResolutionRepository resolutions;
    private final OutcomeCheckInRepository outcomes;
    private final DecisionTemplateFactory templates;
    private final CurrentUserProvider currentUser;
    private final EvidenceImportRepository evidenceImports;
    private final DecisionQuestionRepository questions;
    private final CandidateSuggestionRepository candidateSuggestions;
    private final PreferenceMemoryRepository preferences;
    private final OutcomeReminderRepository reminders;
    private final PublicUrlPolicy urlPolicy;
    private final ReadinessAnalyzer readiness;
    private final WebProperties webProperties;
    private final AiDataPolicy aiDataPolicy;
    private final com.decisionassistant.guidance.DiscoveryService discoveryService;
    private final com.decisionassistant.guidance.ProductDiscoveryRepository discoveries;
    private final Clock clock;

    public DecisionService(DecisionRepository decisions, ResolutionRepository resolutions,
                           OutcomeCheckInRepository outcomes, DecisionTemplateFactory templates,
                           CurrentUserProvider currentUser, EvidenceImportRepository evidenceImports,
                           DecisionQuestionRepository questions, CandidateSuggestionRepository candidateSuggestions,
                           PreferenceMemoryRepository preferences, OutcomeReminderRepository reminders,
                           PublicUrlPolicy urlPolicy, ReadinessAnalyzer readiness, WebProperties webProperties,
                           AiDataPolicy aiDataPolicy,
                           com.decisionassistant.guidance.DiscoveryService discoveryService,
                           com.decisionassistant.guidance.ProductDiscoveryRepository discoveries) {
        this.decisions = decisions;
        this.resolutions = resolutions;
        this.outcomes = outcomes;
        this.templates = templates;
        this.currentUser = currentUser;
        this.evidenceImports = evidenceImports;
        this.questions = questions;
        this.candidateSuggestions = candidateSuggestions;
        this.preferences = preferences;
        this.reminders = reminders;
        this.urlPolicy = urlPolicy;
        this.readiness = readiness;
        this.webProperties = webProperties;
        this.aiDataPolicy = aiDataPolicy;
        this.clock = Clock.systemUTC();
        this.discoveryService = discoveryService; this.discoveries = discoveries;
    }

    @Transactional
    public DecisionResponse create(CreateDecisionRequest request) {
        if (request.discovery() != null) return createDiscovery(request);
        if (request.intentText() != null && !request.intentText().isBlank()) return createGuided(request);
        if (request.title() == null || request.title().isBlank() || request.templateKey() == null) {
            throw new InvalidDecisionException(List.of("결정 설명과 후보 링크 또는 title/templateKey가 필요합니다."));
        }
        var now = Instant.now(clock);
        var decision = new Decision(currentUser.current().id(), request.title(), request.templateKey(),
                templates.version(request.templateKey()), now);
        templates.apply(decision);
        decisions.saveAndFlush(decision);
        return response(decision);
    }

    private DecisionResponse createDiscovery(CreateDecisionRequest request) {
        if ((request.selectedSources() != null && !request.selectedSources().isEmpty())
                || (request.sourceUrls() != null && !request.sourceUrls().isEmpty()))
            throw new InvalidDecisionException(List.of("추천 시작과 직접 후보 추가는 별도로 요청하세요."));
        var template = discoveryService.validate(request.templateKey(), request.discovery());
        var conditions = request.discovery().normalized(template);
        var intent = template.label() + " · " + (conditions.uses().isEmpty() ? "용도 미정" : String.join(" · ", conditions.uses()))
                + (conditions.maxBudget() == null ? "" : " · 최대 " + conditions.maxBudget().toPlainString() + "원")
                + (conditions.compatibility() == null ? "" : " · " + conditions.compatibility())
                + MinimumRequirementMatcher.conditions(template.minimumRequirements(), conditions.selectedRequirements()).stream()
                        .map(item -> " · " + item.label() + " " + item.requiredValue()).collect(Collectors.joining());
        var now = Instant.now(clock);
        var decision = new Decision(currentUser.current().id(), template.label() + " 비교", template.key(), templates.version(template.key()), now);
        var catalog = com.decisionassistant.guidance.CatalogDiscovery.isCatalog(conditions);
        decision.startGuidedIntake(intent, !catalog, !catalog, catalog ? "catalog-v1" : "discovery-v1", now);
        decision.processingStatus(DecisionTypes.ProcessingStatus.NEEDS_INPUT, now);
        decision.updateDraft(decision.title(), new DecisionContext(intent, conditions.maxBudget(), "KRW", request.intentText()), now);
        templates.apply(decision);
        applyConfirmedPreferences(decision);
        if (conditions.maxBudget() != null) decision.constraints().stream().filter(item -> item.name().equals("최대 예산"))
                .forEach(item -> item.update(item.name(), item.kind(), item.criterionId(), conditions.maxBudget(), "KRW", item.description(), true));
        decisions.saveAndFlush(decision);
        discoveryService.start(decision, conditions);
        return response(decision);
    }

    @Transactional
    public DecisionResponse retryDiscovery(UUID id, RetryDiscoveryRequest request) {
        var decision = required(id); ensureEditable(decision);
        discoveryService.retry(decision, request.expectedVersion());
        return response(decision);
    }

    @Transactional
    public DecisionResponse refreshDiscovery(UUID id, RetryDiscoveryRequest request) {
        var decision = decisions.lockByIdAndOwner(id, currentUser.current().id()).orElseThrow(() -> new NotFoundException("비교를 찾을 수 없습니다."));
        ensureEditable(decision);
        discoveryService.refresh(decision, request.expectedVersion());
        return response(decision);
    }

    private DecisionResponse createGuided(CreateDecisionRequest request) {
        if (Boolean.TRUE.equals(request.aiConsent())) {
            aiDataPolicy.validateSensitiveDataAcknowledgement(Boolean.TRUE.equals(request.sensitiveDataAcknowledged()));
        }
        if (request.selectedSources() != null && request.sourceUrls() != null && !request.sourceUrls().isEmpty()) {
            throw new InvalidDecisionException(List.of("selectedSources와 sourceUrls 중 하나만 제공하세요."));
        }
        var sources = request.selectedSources() != null ? request.selectedSources()
                : request.sourceUrls() == null ? List.<AddSourceRequest>of() : request.sourceUrls().stream()
                .filter(value -> value != null && !value.isBlank()).map(url -> new AddSourceRequest(url, null)).toList();
        var urls = sources.stream().map(source -> urlPolicy.validate(source.url())).distinct().toList();
        if (urls.isEmpty()) throw new InvalidDecisionException(List.of("비교를 시작할 공개 후보 URL을 하나 이상 추가하세요."));
        var now = Instant.now(clock);
        var template = request.templateKey() == null ? inferTemplate(request.intentText(), urls) : request.templateKey();
        if (template == DecisionTypes.TemplateKey.CUSTOM) template = DecisionTypes.TemplateKey.LAPTOP;
        var title = request.title() == null || request.title().isBlank()
                ? conciseTitle(request.intentText()) : request.title().trim();
        var decision = new Decision(currentUser.current().id(), title, template, templates.version(template), now);
        decision.startGuidedIntake(request.intentText().trim(), Boolean.TRUE.equals(request.aiConsent()),
                Boolean.TRUE.equals(request.webConsent()), "guided-v1", now);
        if (!webProperties.configured()) decision.processingStatus(DecisionTypes.ProcessingStatus.NEEDS_INPUT, now);
        decision.updateDraft(title, new DecisionContext(request.intentText().trim(), null, "KRW", null), now);
        templates.apply(decision);
        applyConfirmedPreferences(decision);
        urls.forEach(url -> decision.addOption(null, sourceName(sources.stream()
                .filter(source -> url.equals(urlPolicy.validate(source.url()))).findFirst().orElseThrow().name(), url),
                null, url, null));
        decisions.saveAndFlush(decision);
        if (decision.webConsent() && webProperties.configured()) decision.options().forEach(option ->
                evidenceImports.save(new EvidenceImport(decision.id(), option.id(), option.sourceUrl(), now)));
        seedQuestions(decision, now);
        log.info("product_event=guided_decision_started decision_id={} owner_id={} source_count={} template={}",
                decision.id(), decision.ownerUserId(), urls.size(), decision.templateKey());
        return response(decision);
    }

    @Transactional(readOnly = true)
    public DecisionPage list(Set<DecisionTypes.DecisionStatus> statuses, int page, int size,
                             ListSort sort, ListDirection direction) {
        var selected = statuses == null ? java.util.EnumSet.allOf(DecisionTypes.DecisionStatus.class) : statuses;
        if (selected.isEmpty()) return new DecisionPage(List.of(), 0, size, 0, 0);
        var property = switch (sort) {
            case UPDATED_AT -> "updatedAt"; case CREATED_AT -> "createdAt";
            case TITLE -> "title"; case TEMPLATE_KEY -> "templateKey";
        };
        var ordering = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.valueOf(direction.name()), property).and(
                org.springframework.data.domain.Sort.by("id"));
        var owner = currentUser.current().id();
        var result = decisions.findSummaries(owner, selected, org.springframework.data.domain.PageRequest.of(page, size, ordering));
        // 삭제로 마지막 페이지가 사라졌거나 오래된 링크로 진입하면 마지막 유효 페이지를 반환한다.
        if (page > 0 && page >= result.getTotalPages()) {
            result = decisions.findSummaries(owner, selected,
                    org.springframework.data.domain.PageRequest.of(Math.max(0, result.getTotalPages() - 1), size, ordering));
        }
        return new DecisionPage(result.getContent(), result.getNumber(), size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<DecisionSummary> selection(Set<DecisionTypes.DecisionStatus> statuses) {
        var selected = statuses == null ? java.util.EnumSet.allOf(DecisionTypes.DecisionStatus.class) : statuses;
        return selected.isEmpty() ? List.of() : decisions.findSelection(currentUser.current().id(), selected);
    }

    @Transactional(readOnly = true)
    public List<StatusCount> counts() {
        var counts = decisions.countStatuses(currentUser.current().id()).stream()
                .collect(Collectors.toMap(StatusCount::status, StatusCount::count));
        return java.util.Arrays.stream(DecisionTypes.DecisionStatus.values())
                .map(status -> new StatusCount(status, counts.getOrDefault(status, 0L))).toList();
    }

    @Transactional
    public void delete(UUID id, long expectedVersion) {
        var decision = decisions.lockByIdAndOwner(id, currentUser.current().id())
                .orElseThrow(() -> new NotFoundException("결정을 찾을 수 없습니다."));
        if (decision.version() != expectedVersion) throw new ConflictException("결정이 변경되었습니다. 최신 목록을 확인한 뒤 다시 삭제하세요.");
        decision.trash(Instant.now(clock));
        var deletedAt = decision.deletedAt();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        log.info("product_event=decision_trashed decision_id={} deleted_at={}", id, deletedAt);
                    }
                });
    }

    @Transactional(readOnly = true)
    public DecisionResponse get(UUID id) {
        return response(required(id));
    }

    @Transactional
    public DecisionResponse addSource(UUID id, AddSourceRequest request) {
        var decision = required(id);
        ensureEditable(decision);
        var url = urlPolicy.validate(request.url());
        if (decision.options().stream().anyMatch(option -> url.equals(option.sourceUrl()))) return response(decision);
        if (decision.options().size() >= 5) throw new InvalidDecisionException(List.of("후보는 최대 5개까지 추가할 수 있습니다."));
        var now = Instant.now(clock);
        var option = decision.addOption(null, sourceName(request.name(), url), null, url, null);
        decision.updateDraft(decision.title(), decision.context(), now);
        if (decision.webConsent() && webProperties.configured()) {
            evidenceImports.save(new EvidenceImport(decision.id(), option.id(), url, now));
            decision.processingStatus(DecisionTypes.ProcessingStatus.IMPORTING, now);
        }
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public DecisionResponse retryEvidence(UUID id, UUID evidenceId) {
        var decision = required(id);
        ensureEditable(decision);
        if (!webProperties.configured()) throw new ConflictException("웹 수집 provider가 비활성화되어 있습니다.");
        var evidence = evidenceImports.findByIdAndDecisionId(evidenceId, id)
                .orElseThrow(() -> new NotFoundException("Evidence import를 찾을 수 없습니다."));
        evidence.retry(Instant.now(clock));
        decision.processingStatus(DecisionTypes.ProcessingStatus.IMPORTING, Instant.now(clock));
        return response(decision);
    }

    @Transactional
    public DecisionResponse answerQuestion(UUID id, UUID questionId, AnswerQuestionRequest request) {
        var decision = required(id);
        ensureEditable(decision);
        var question = questions.findByIdAndDecisionId(questionId, id)
                .orElseThrow(() -> new NotFoundException("확인 질문을 찾을 수 없습니다."));
        var answer = request.answer().trim();
        var context = decision.context();
        if ("context.budget".equals(question.targetKey())) {
            try {
                decision.updateDraft(decision.title(), new DecisionContext(context.intendedUse(),
                        new BigDecimal(answer.replace(",", "")), context.currency(), context.notes()), Instant.now(clock));
            } catch (NumberFormatException exception) {
                throw new InvalidDecisionException(List.of("예산은 숫자로 입력하세요."));
            }
        } else if ("priority".equals(question.targetKey())) {
            decision.criteria().stream().filter(item -> item.name().equals(answer)).findFirst().ifPresent(item ->
                    item.update(item.name(), item.kind(), item.direction(), BigDecimal.valueOf(3),
                            item.worstAnchor(), item.bestAnchor(), item.unit(), item.active()));
        } else {
            var notes = context.notes() == null || context.notes().isBlank() ? answer : context.notes() + "\n" + answer;
            decision.updateDraft(decision.title(), new DecisionContext(context.intendedUse(), context.budget(),
                    context.currency(), notes), Instant.now(clock));
        }
        question.answer(answer, Instant.now(clock));
        decision.updateDraft(decision.title(), decision.context(), Instant.now(clock));
        log.info("product_event=decision_question_answered decision_id={} question_id={} target={}",
                decision.id(), question.id(), question.targetKey());
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public DecisionResponse updateSuggestion(UUID id, UUID suggestionId, UpdateSuggestionRequest request) {
        var ownerId = currentUser.current().id();
        var decision = decisions.lockByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("비교를 찾을 수 없습니다."));
        ensureEditable(decision);
        var suggestion = candidateSuggestions.findByIdAndDecisionId(suggestionId, id)
                .orElseThrow(() -> new NotFoundException("대안 후보를 찾을 수 없습니다."));
        if (suggestion.status() != CandidateSuggestion.Status.PROPOSED) return response(decision);
        if ("DISMISS".equalsIgnoreCase(request.action())) {
            suggestion.dismiss();
            return response(decision);
        }
        if (!"ACCEPT".equalsIgnoreCase(request.action())) {
            throw new InvalidDecisionException(List.of("action은 ACCEPT 또는 DISMISS여야 합니다."));
        }
        var existing = decision.options().stream().filter(option -> suggestion.discovered()
                ? option.name().equalsIgnoreCase(suggestion.name()) : suggestion.url().equals(option.sourceUrl())).findFirst();
        if (existing.isPresent()) { suggestion.accept(existing.get().id()); return response(decision); }
        if (decision.options().size() >= 5) throw new InvalidDecisionException(List.of("Option은 최대 5개까지 추가할 수 있습니다."));
        var url = urlPolicy.validate(suggestion.url());
        var now = Instant.now(clock);
        var option = decision.addOption(null, suggestion.name(), suggestion.reason(), url, suggestion.sourceExcerpt());
        suggestion.accept(option.id());
        decision.updateDraft(decision.title(), decision.context(), now);
        if (!suggestion.discovered() && decision.webConsent() && webProperties.configured()) {
            evidenceImports.save(new EvidenceImport(id, option.id(), url, now));
            decision.processingStatus(DecisionTypes.ProcessingStatus.IMPORTING, now);
        }
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public DecisionResponse confirmValue(UUID id, UUID valueId, ConfirmValueRequest request) {
        var decision = required(id);
        ensureEditable(decision);
        if (!decision.confirmValue(valueId, request.numericValue(), request.rating(),
                Boolean.TRUE.equals(request.disputed()), Instant.now(clock))) {
            throw new NotFoundException("비교 값을 찾을 수 없습니다.");
        }
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public DecisionResponse update(UUID id, UpdateDraftRequest request) {
        var decision = required(id);
        if (decision.version() != request.expectedVersion()) {
            throw new ConflictException("Decision version이 변경되었습니다. 최신 draft를 다시 불러오세요.");
        }
        if (decision.status() != DecisionTypes.DecisionStatus.DRAFT) {
            throw new ConflictException("선택 또는 포기된 Decision은 draft를 수정할 수 없습니다.");
        }

        decision.updateDraft(request.title(), new DecisionContext(
                request.context().intendedUse(), request.context().budget(),
                normalizeCurrency(request.context().currency()), request.context().notes()), Instant.now(clock));

        syncOptions(decision, request.options());
        if (decision.comparisonState() != null) {
            var state = decision.comparisonState();
            var settings = state.settings();
            var ids = decision.options().stream().map(DecisionOption::id).toList();
            decision.comparisonState(new Comparison.State(new Comparison.Settings(settings.maxBudget(), settings.ancRequired(),
                    settings.compatibility(), settings.focus(), settings.deferredOptionIds().stream().filter(ids::contains).toList()),
                    state.confirmations().stream().filter(item -> ids.contains(item.optionId())).toList()), Instant.now(clock));
        }
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
        var decision = locked(id);
        ensureEditable(decision);

        if (decision.editMode() != null && request.expectedVersion() == null)
            throw new ConflictException("기록 수정·재검토는 최신 version으로 완료하세요.");
        if (request.expectedVersion() != null && request.expectedVersion() != decision.version())
            throw new ConflictException("비교가 변경되었습니다. 최신 내용을 확인한 뒤 기록하세요.");
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
                id, request.kind(), request.selectedOptionId(), request.rationale(), now, Comparison.Snapshot.capture(response(decision), now)));
        resolution.reasons(DecisionReasons.validate(request.kind(), request.reasonIds(), request.primaryReasonId()), request.primaryReasonId());
        resolution.revision(decision.editMode(), decision.revisionBaseId());
        if (decision.editMode() == DecisionTypes.EditMode.CORRECTION) {
            resolutions.findById(decision.revisionBaseId()).orElseThrow().supersede(now);
            preferences.supersedeForResolution(id, decision.revisionBaseId(), now);
        }
        decision.resolve(switch (request.kind()) {
            case CHOSEN -> DecisionTypes.DecisionStatus.CHOSEN;
            case DEFERRED -> DecisionTypes.DecisionStatus.DEFERRED;
            case ABANDONED -> DecisionTypes.DecisionStatus.ABANDONED;
        }, now);
        decision.recordResolution(resolution.id(), true);

        log.info("product_event=decision_resolved decision_id={} kind={}", id, request.kind());
        return resolutionData(resolution);
    }

    @Transactional
    public DecisionResponse beginRevision(UUID id, BeginRevisionRequest request) {
        var decision = locked(id);
        if (decision.version() != request.expectedVersion()) throw new ConflictException("결정이 변경되었습니다. 최신 내용을 확인하세요.");
        if (DecisionTypes.phase(decision.status()) != DecisionTypes.DecisionPhase.COMPLETED)
            throw new ConflictException("완료된 결정만 기록 수정 또는 다시 검토를 시작할 수 있습니다.");
        var base = currentResolution(decision);
        if (base == null) throw new ConflictException("완료 기록을 찾을 수 없습니다.");
        reminders.findByDecisionIdOrderByDueAtAsc(id).stream()
                .filter(r -> r.status() == OutcomeReminder.Status.PENDING || r.status() == OutcomeReminder.Status.SENT)
                .forEach(OutcomeReminder::skip);
        if (base.purchaseStatus() == Resolution.PurchaseStatus.PURCHASED) throw new ConflictException("구매한 선택은 재검토할 수 없습니다.");
        if (!Boolean.TRUE.equals(request.notPurchased())) throw new ConflictException("아직 구매하지 않았는지 확인하세요.");
        decision.revisionDraft(response(decision));
        decision.revisionCandidates(candidateSuggestions.findByDecisionIdOrderByCreatedAtAsc(id).stream()
                .map(item -> new Decision.RevisionCandidateState(item.id(), item.status(), item.optionId())).toList());
        decision.beginRevision(request.mode(), base.id(), Instant.now(clock));
        decisions.flush();
        return response(decision);
    }

    private Decision locked(UUID id) {
        return decisions.lockByIdAndOwner(id, currentUser.current().id())
                .orElseThrow(() -> new NotFoundException("결정을 찾을 수 없습니다."));
    }

    @Transactional
    public DecisionResponse purchase(UUID id, PurchaseRequest request) {
        var decision = locked(id);
        var resolution = currentResolution(decision);
        if (decision.version() != request.expectedVersion() || decision.status() != DecisionTypes.DecisionStatus.CHOSEN
                || resolution == null || !resolution.id().equals(request.resolutionId()))
            throw new ConflictException("현재 제품 선택과 최신 내용을 확인하세요.");
        if (resolution.purchaseStatus() == Resolution.PurchaseStatus.PURCHASED)
            throw new ConflictException("구매 완료 기록은 변경할 수 없습니다.");
        resolution.purchase(request.status(), Instant.now(clock));
        decision.updateDraft(decision.title(), decision.context(), Instant.now(clock));
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public DecisionResponse cancelRevision(UUID id, VersionRequest request) {
        var decision = locked(id);
        if (decision.version() != request.expectedVersion() || decision.editMode() == null || decision.revisionDraft() == null)
            throw new ConflictException("취소할 재검토를 찾을 수 없습니다. 최신 내용을 확인하세요.");
        var saved = decision.revisionDraft();
        decision.updateDraft(saved.title(), new DecisionContext(saved.context().intendedUse(), saved.context().budget(), saved.context().currency(), saved.context().notes()), Instant.now(clock));
        syncOptions(decision, saved.options()); syncCriteria(decision, saved.criteria()); syncConstraints(decision, saved.constraints());
        syncValues(decision, saved.values()); syncConstraintResults(decision, saved.constraintResults());
        if (saved.comparison() != null) decision.comparisonState(new Comparison.State(saved.comparison().settings(), saved.comparison().confirmations()), Instant.now(clock));
        var previousCandidates = decision.revisionCandidates();
        var savedById = previousCandidates == null ? java.util.Map.<UUID, Decision.RevisionCandidateState>of()
                : previousCandidates.stream().collect(Collectors.toMap(Decision.RevisionCandidateState::id, Function.identity()));
        var optionIds = saved.options().stream().map(OptionData::id).collect(Collectors.toSet());
        for (var candidate : candidateSuggestions.findByDecisionIdOrderByCreatedAtAsc(id)) {
            var previous = savedById.get(candidate.id());
            if (previous != null) candidate.restoreSelection(previous.status(), previous.optionId());
            else if (previousCandidates != null || candidate.status() == CandidateSuggestion.Status.ACCEPTED
                    && !optionIds.contains(candidate.optionId())) {
                // 재검토 중 새로 생긴 후보는 선택 전 상태로 남긴다. 이전 버전 복원본은 끊긴 연결만 복구한다.
                candidate.restoreSelection(CandidateSuggestion.Status.PROPOSED, null);
            }
        }
        decision.cancelRevision(Instant.now(clock)); decisions.flush();
        return response(decision);
    }

    private Resolution currentResolution(Decision decision) {
        if (decision.currentResolutionId() != null) return resolutions.findById(decision.currentResolutionId()).orElse(null);
        return resolutions.findByDecisionIdOrderByCreatedAtDesc(decision.id()).stream().findFirst().orElse(null);
    }

    private Comparison.State comparisonState(Decision decision) {
        if (decision.comparisonState() != null) return decision.comparisonState();
        var run = discoveries.findById(decision.id()).orElse(null);
        return Comparison.initial(decision, run == null ? null : run.conditions());
    }

    private Decision editableVersion(UUID id, long version) {
        var decision = required(id);
        ensureEditable(decision);
        if (decision.version() != version) throw new ConflictException("비교가 변경되었습니다. 최신 내용을 확인한 뒤 다시 적용하세요.");
        return decision;
    }

    @Transactional
    public DecisionResponse updateComparison(UUID id, Comparison.UpdateRequest request) {
        var decision = editableVersion(id, request.expectedVersion());
        Comparison.validate(decision, request.settings());
        decision.comparisonState(new Comparison.State(request.settings(), comparisonState(decision).confirmations()), Instant.now(clock));
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public DecisionResponse confirmFeature(UUID id, Comparison.ConfirmRequest request) {
        var decision = editableVersion(id, request.expectedVersion());
        var state = comparisonState(decision);
        Comparison.validate(decision, state.settings());
        if (decision.options().stream().noneMatch(option -> option.id().equals(request.optionId()))
                || !List.of("anc", "Android", "iOS", "멀티포인트").contains(request.feature()))
            throw new InvalidDecisionException(List.of("현재 후보와 확인 가능한 기능을 지정하세요."));
        var sourceUrl = request.sourceUrl() == null || request.sourceUrl().isBlank() ? null : urlPolicy.validate(request.sourceUrl());
        var confirmations = new ArrayList<>(state.confirmations().stream()
                .filter(c -> !(c.optionId().equals(request.optionId()) && c.feature().equals(request.feature()))).toList());
        if (request.supported() != null) confirmations.add(new Comparison.Confirmation(request.optionId(), request.feature(),
                request.supported(), request.note(), sourceUrl, Instant.now(clock)));
        decision.comparisonState(new Comparison.State(state.settings(), List.copyOf(confirmations)), Instant.now(clock));
        decisions.flush();
        return response(decision);
    }

    @Transactional
    public OutcomeData addOutcome(UUID id, CreateOutcomeRequest request) {
        var decision = locked(id);
        if (request.expectedVersion() != null && request.expectedVersion() != decision.version())
            throw new ConflictException("결정이 변경되었습니다. 최신 기록을 확인하세요.");
        if (DecisionTypes.phase(decision.status()) != DecisionTypes.DecisionPhase.COMPLETED) {
            throw new ConflictException("회고는 완료된 결정에만 기록할 수 있습니다.");
        }
        var resolution = currentResolution(decision);
        if (resolution == null) throw new ConflictException("회고를 연결할 완료 기록을 찾을 수 없습니다.");
        if (resolution.editMode() != null && (request.resolutionId() == null || request.expectedVersion() == null))
            throw new ConflictException("정정·재검토 후에는 회고 대상 기록과 최신 version이 필요합니다.");
        if (request.resolutionId() != null && !request.resolutionId().equals(resolution.id()))
            throw new ConflictException("회고 대상 결정이 변경되었습니다. 최신 기록을 확인하세요.");
        var outcome = outcomes.save(new OutcomeCheckIn(
                id, request.satisfaction(), request.expectationStatus(), request.notes(), request.wouldChooseAgain(),
                request.workedWell(), request.disappointedBy(), Instant.now(clock)));
        outcome.resolution(resolution.id());
        var activeReminders = reminders.findByDecisionIdOrderByDueAtAsc(id).stream()
                .filter(item -> item.status() == OutcomeReminder.Status.PENDING || item.status() == OutcomeReminder.Status.SENT)
                .toList();
        activeReminders.stream().map(OutcomeReminder::dueAt).min(Instant::compareTo).ifPresent(firstDue ->
                activeReminders.stream().filter(item -> item.dueAt().equals(firstDue)).forEach(OutcomeReminder::complete));
        if (decision.status() == DecisionTypes.DecisionStatus.CHOSEN) proposeMemories(decision, outcome, request);
        log.info("product_event=outcome_recorded decision_id={} outcome_id={}", id, outcome.id());
        return outcomeData(outcome);
    }

    @Transactional(readOnly = true)
    public Decision required(UUID id) {
        return decisions.findByIdAndOwnerUserId(id, currentUser.current().id())
                .orElseThrow(() -> new NotFoundException("Decision을 찾을 수 없습니다: " + id));
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
            var saved = decision.mutableValues().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
            if (saved != null && input.origin() != null && input.verificationStatus() != null) {
                saved.markGenerated(input.origin(), input.verificationStatus());
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

    private void ensureEditable(Decision decision) {
        if (decision.status() != DecisionTypes.DecisionStatus.DRAFT) {
            throw new ConflictException("선택 또는 포기된 Decision은 수정할 수 없습니다.");
        }
    }

    private DecisionTypes.TemplateKey inferTemplate(String intent, List<String> urls) {
        var text = (intent + " " + String.join(" ", urls)).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("이어폰") || text.contains("earbud") || text.contains("earphone")
                || text.contains("airpods") || text.contains("버즈")) return DecisionTypes.TemplateKey.EARBUDS;
        if (text.contains("monitor") || text.contains("모니터") || text.contains("display")) return DecisionTypes.TemplateKey.MONITOR;
        if (text.contains("tablet") || text.contains("태블릿") || text.contains("ipad")) return DecisionTypes.TemplateKey.TABLET;
        if (text.contains("phone") || text.contains("스마트폰") || text.contains("휴대폰") || text.contains("iphone")) return DecisionTypes.TemplateKey.PHONE;
        return DecisionTypes.TemplateKey.LAPTOP;
    }

    private String conciseTitle(String intent) {
        var normalized = intent.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String sourceName(String name, String url) {
        return name == null || name.isBlank() ? optionName(url) : name.strip();
    }

    private String optionName(String url) {
        try {
            var host = URI.create(url).getHost();
            return host == null ? "후보 제품" : "후보 · " + host.replaceFirst("^www\\.", "");
        } catch (RuntimeException exception) {
            return "후보 제품";
        }
    }

    private void seedQuestions(Decision decision, Instant now) {
        questions.save(new DecisionQuestion(decision.id(), DecisionQuestion.Kind.NUMBER, "context.budget",
                "이번 구매의 최대 예산은 얼마인가요?", "비교 참고용 예산으로 기록합니다. 자동 제외 조건은 별도 설정합니다.", List.of(), 1, now));
        questions.save(new DecisionQuestion(decision.id(), DecisionQuestion.Kind.SINGLE_CHOICE, "priority",
                "이번 결정에서 가장 중요한 기준은 무엇인가요?", "선택한 기준의 중요도를 3으로 설정합니다. 초기 기본값은 모두 1입니다.",
                decision.criteria().stream().filter(Criterion::active).map(Criterion::name).limit(6).toList(), 2, now));
        questions.save(new DecisionQuestion(decision.id(), DecisionQuestion.Kind.TEXT, "context.avoid",
                "반드시 피하고 싶은 단점이나 조건이 있나요?", "비교할 때 확인할 메모로 남깁니다. 자동 제외되지는 않습니다.", List.of(), 3, now));
    }

    private void applyConfirmedPreferences(Decision decision) {
        preferences.findByOwnerUserIdAndStatusAndTemplateKey(currentUser.current().id(),
                PreferenceMemory.Status.CONFIRMED, decision.templateKey()).forEach(memory ->
                decision.criteria().stream().filter(item -> item.name().equals(memory.criterionName())).findFirst()
                        .ifPresent(item -> item.update(item.name(), item.kind(), item.direction(),
                                BigDecimal.valueOf(Math.max(1, Math.min(3, memory.strength()))), item.worstAnchor(),
                                item.bestAnchor(), item.unit(), item.active())));
    }

    private void proposeMemories(Decision decision, OutcomeCheckIn outcome, CreateOutcomeRequest request) {
        if (request.workedWell() != null && !request.workedWell().isBlank()) {
            preferences.save(new PreferenceMemory(decision.ownerUserId(), decision.templateKey(), request.workedWell(),
                    "‘%s’을 중요하게 본 선택이 실제 사용에서 잘 맞았습니다.".formatted(request.workedWell()),
                    3, decision.id(), outcome.id(), Instant.now(clock)));
        }
        if (request.disappointedBy() != null && !request.disappointedBy().isBlank()) {
            preferences.save(new PreferenceMemory(decision.ownerUserId(), decision.templateKey(), request.disappointedBy(),
                    "다음 %s 비교에서는 ‘%s’을 더 엄격히 확인합니다."
                            .formatted(categoryLabel(decision.templateKey()), request.disappointedBy()),
                    2, decision.id(), outcome.id(), Instant.now(clock)));
        }
    }

    private String categoryLabel(DecisionTypes.TemplateKey key) {
        return switch (key) { case LAPTOP -> "노트북"; case PHONE -> "휴대폰"; case TABLET -> "태블릿";
            case MONITOR -> "모니터"; case EARBUDS -> "무선 이어폰"; case CUSTOM -> "구매"; };
    }

    private DecisionResponse response(Decision decision) {
        var run = discoveries.findById(decision.id()).orElse(null);
        var selected = run == null ? java.util.Map.<String, String>of() : run.conditions().selectedRequirements();
        var discoveryBudget = run == null ? decision.context().budget() : run.conditions().maxBudget();
        var suggestions = candidateSuggestions.findByDecisionIdOrderByCreatedAtAsc(decision.id());
        var evidence = evidenceImports.findByDecisionIdOrderByCreatedAtAsc(decision.id());
        var productFacts = decision.options().stream().map(option -> {
            var source = suggestions.stream().filter(item -> option.id().equals(item.optionId()) && item.discovered()).findFirst().orElse(null);
            var fetchedAt = evidence.stream().filter(item -> option.id().equals(item.optionId())).map(EvidenceImport::fetchedAt)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            var facts = ProductFactResolver.resolve(decision, option.id(), source == null ? List.of() : source.facts(), fetchedAt);
            return new ProductFactsData(option.id(), facts, source == null ? fetchedAt : source.fetchedAt(),
                    source == null ? null : source.sourceExcerpt(), requirementChecks(decision, selected, facts),
                    source == null ? null : source.officialOffer(), OfficialStorePolicy.sourceKind(option.sourceUrl(), source != null && source.officialOffer() != null),
                    com.decisionassistant.guidance.CompatibilityMatcher.check(decision.templateKey(), run == null ? null : run.conditions().compatibility(), facts,
                            source == null ? null : source.officialOffer(), source != null && source.catalog()));
        }).toList();
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
                        value.certainty(), value.sourceUrl(), value.sourceNote(), value.origin(),
                        value.verificationStatus())).toList(),
                decision.constraints().stream().map(constraint -> new ConstraintData(
                        constraint.id(), constraint.name(), constraint.kind(), constraint.criterionId(),
                        constraint.threshold(), constraint.unit(), constraint.description(), constraint.active())).toList(),
                decision.constraintResults().stream().map(result -> new ConstraintResultData(
                        result.id(), result.optionId(), result.constraintId(), result.status(), result.note())).toList(),
                resolutions.findByDecisionIdOrderByCreatedAtDesc(decision.id()).stream().map(this::resolutionData).toList(),
                outcomes.findByDecisionIdOrderByCreatedAtDesc(decision.id()).stream().map(this::outcomeData).toList(),
                guidance(decision, run == null ? suggestions : suggestions.stream().filter(item -> !item.discovered() || item.generation() == run.displayedGeneration()).toList(), evidence, selected, discoveryBudget),
                decision.createdAt(), decision.updatedAt(),
                run == null ? null : new DiscoveryData(run.version(), run.status().name(), run.error(), run.conditions(), run.updatedAt(),
                        run.policyVersion(), run.stage(), run.executionId(), run.emptyReason(), run.generation(), run.displayedGeneration(),
                        run.history().stream().filter(round -> round.generation() != run.displayedGeneration()).map(round -> new DiscoveryHistoryData(
                                round.generation(), round.status(), round.error(), round.emptyReason(), round.policyVersion(), round.completedAt(),
                                suggestions.stream().filter(item -> item.discovered() && item.generation() == round.generation()).sorted(DiscoveryRanking.byPrice())
                                        .map(item -> candidateData(decision, selected, item, discoveryBudget)).toList())).toList()),
                productFacts, decision.templateKey() == DecisionTypes.TemplateKey.EARBUDS
                        ? Comparison.calculate(comparisonState(decision), productFacts, Instant.now(clock)) : null,
                DecisionTypes.phase(decision.status()), DecisionTypes.result(decision.status()), decision.editMode(),
                decision.currentResolutionId() != null ? decision.currentResolutionId() :
                        (DecisionTypes.phase(decision.status()) == DecisionTypes.DecisionPhase.COMPLETED && currentResolution(decision) != null
                                ? currentResolution(decision).id() : null), decision.revisionBaseId(),
                decision.personalizationEnabled());
    }

    private List<DiscoveryContracts.RequirementCheck> requirementChecks(Decision decision, java.util.Map<String, String> selected,
                                                                       List<DiscoveryContracts.ProductFact> facts) {
        return MinimumRequirementMatcher.check(MinimumRequirements.forCategory(decision.templateKey()), selected, facts);
    }

    private ResolutionData resolutionData(Resolution resolution) {
        return new ResolutionData(resolution.id(), resolution.kind(), resolution.selectedOptionId(),
                resolution.rationale(), resolution.createdAt(), resolution.comparisonSnapshot(),
                resolution.editMode(), resolution.previousResolutionId(), resolution.supersededAt(), resolution.reasonIds(), resolution.primaryReasonId(), resolution.purchaseStatus(), resolution.purchaseRecordedAt());
    }

    private OutcomeData outcomeData(OutcomeCheckIn outcome) {
        return new OutcomeData(outcome.id(), outcome.satisfaction(), outcome.expectationStatus(),
                outcome.notes(), outcome.wouldChooseAgain(), outcome.workedWell(), outcome.disappointedBy(),
                outcome.createdAt(), outcome.resolutionId());
    }

    private GuidanceData guidance(Decision decision, List<CandidateSuggestion> suggestions, List<EvidenceImport> evidence,
                                  java.util.Map<String, String> selected, BigDecimal discoveryBudget) {
        if (decision.intentText() == null) return null;
        var analyzed = readiness.analyze(decision);
        var processing = decision.processingStatus();
        var summary = processing == DecisionTypes.ProcessingStatus.IMPORTING
                ? "후보 링크에서 제품 정보와 근거를 가져오는 중입니다." : analyzed.summary();
        var applied = preferences.findByOwnerUserIdAndStatusAndTemplateKey(decision.ownerUserId(),
                PreferenceMemory.Status.CONFIRMED, decision.templateKey()).stream().map(this::preferenceData).toList();
        return new GuidanceData(decision.intentText(), decision.aiConsent(), decision.webConsent(), processing, analyzed.readiness(), summary,
                questions.findTop3ByDecisionIdAndStatusOrderByPriorityAscCreatedAtAsc(
                        decision.id(), DecisionQuestion.Status.OPEN).stream().map(question -> new QuestionData(
                        question.id(), question.kind().name(), question.targetKey(), question.prompt(), question.impact(),
                        question.options(), question.answer(), question.status().name())).toList(),
                evidence.stream().map(item -> new EvidenceData(
                        item.id(), item.optionId(), item.url(), item.status().name(), item.title(), item.excerpt(),
                        item.error(), item.fetchedAt(), item.fetchedAt() != null
                                && item.fetchedAt().isBefore(Instant.now(clock).minus(24, ChronoUnit.HOURS)))).toList(),
                suggestions.stream()
                        .filter(item -> item.status() == CandidateSuggestion.Status.PROPOSED)
                        .sorted(DiscoveryRanking.byPrice())
                        .map(item -> candidateData(decision, selected, item, discoveryBudget)).toList(),
                applied,
                reminders.findByDecisionIdOrderByDueAtAsc(decision.id()).stream().map(item -> new ReminderData(
                        item.id(), item.dueAt(), item.status().name(), item.channel().name())).toList());
    }

    private PreferenceData preferenceData(PreferenceMemory item) {
        return new PreferenceData(item.id(), item.templateKey(), item.criterionName(), item.statement(), item.strength(),
                item.status().name(), item.confidence().name(), item.sourceDecisionId());
    }
    private CandidateSuggestionData candidateData(Decision decision, java.util.Map<String, String> selected, CandidateSuggestion item, BigDecimal discoveryBudget) {
        var checks = requirementChecks(decision, selected, item.facts());
        var run = discoveries.findById(decision.id()).orElse(null);
        var compatibility = com.decisionassistant.guidance.CompatibilityMatcher.check(decision.templateKey(), run == null ? null : run.conditions().compatibility(), item.facts(), item.officialOffer(), item.catalog());
        var reason = item.catalog() && run != null ? com.decisionassistant.guidance.CatalogDiscovery.reason(decision, run.conditions(), item,
                new com.decisionassistant.template.IntakeTemplates().required(decision.templateKey(), run.conditions().templateVersion())) : item.reason();
        return new CandidateSuggestionData(item.id(), item.name(), item.url(), reason, item.status().name(), item.facts(), item.fetchedAt(),
                item.sourceExcerpt(), checks, item.officialOffer(), OfficialStorePolicy.sourceKind(item.url(), item.officialOffer() != null),
                item.catalog() ? DiscoveryRanking.catalogGroup(item.officialOffer(), checks, discoveryBudget, compatibility) : DiscoveryRanking.group(item.officialOffer(), checks, discoveryBudget, compatibility), compatibility,
                item.catalog() ? com.decisionassistant.guidance.CatalogDiscovery.priceStatus(item.officialOffer()) : null);
    }
}
