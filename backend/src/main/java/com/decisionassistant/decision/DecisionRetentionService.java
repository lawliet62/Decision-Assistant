package com.decisionassistant.decision;

import com.decisionassistant.common.ConflictException;
import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.identity.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class DecisionRetentionService {
    public static final int RETENTION_DAYS = 30;
    public enum RetentionView { ACTIVE, ARCHIVE, TRASH }
    public enum RetentionAction { ARCHIVE, UNARCHIVE, RESTORE, PERSONALIZATION_ON, PERSONALIZATION_OFF }
    public record RetentionChange(@jakarta.validation.constraints.Min(0) long expectedVersion,
                         @jakarta.validation.constraints.NotNull RetentionAction action) {}
    public record RetentionItem(UUID id, long version, String title, DecisionTypes.DecisionStatus status,
                       Instant archivedAt, Instant deletedAt, Instant recoverableUntil, boolean recoverable, boolean personalizationEnabled) {}
    public record RetentionPage(List<RetentionItem> items, int page, boolean hasNext) {}

    private final EntityManager em;
    private final CurrentUserProvider user;
    public DecisionRetentionService(EntityManager em, CurrentUserProvider user) { this.em = em; this.user = user; }

    @Transactional(readOnly = true)
    public RetentionPage list(RetentionView view, int page) {
        String filter = switch (view) {
            case ACTIVE -> "d.deletedAt is null and d.archivedAt is null";
            case ARCHIVE -> "d.deletedAt is null and d.archivedAt is not null";
            case TRASH -> "d.deletedAt is not null";
        };
        var rows = em.createQuery("select d from Decision d where d.ownerUserId = :owner and " + filter
                        + " order by d.updatedAt desc, d.id", Decision.class)
                .setParameter("owner", user.current().id()).setFirstResult(page * 10).setMaxResults(11).getResultList();
        return new RetentionPage(rows.stream().limit(10).map(this::item).toList(), page, rows.size() > 10);
    }

    @Transactional
    public RetentionItem change(UUID id, RetentionChange request) {
        var d = em.createQuery("select d from Decision d where d.id = :id and d.ownerUserId = :owner", Decision.class)
                .setParameter("id", id).setParameter("owner", user.current().id())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst()
                .orElseThrow(() -> new NotFoundException("결정을 찾을 수 없습니다."));
        if (d.version() != request.expectedVersion()) throw new ConflictException("결정이 변경되었습니다. 목록을 새로고침하세요.");
        var now = Instant.now();
        if (request.action() == RetentionAction.RESTORE) {
            if (d.deletedAt() == null || !now.isBefore(d.deletedAt().plus(RETENTION_DAYS, ChronoUnit.DAYS)))
                throw new ConflictException("복구 가능한 기간이 아닙니다.");
            d.restore(now);
        } else {
            if (d.deletedAt() != null) throw new NotFoundException("휴지통의 결정은 먼저 복구해야 합니다.");
            switch (request.action()) {
                case ARCHIVE -> {
                    if (DecisionTypes.phase(d.status()) != DecisionTypes.DecisionPhase.COMPLETED)
                        throw new InvalidDecisionException(List.of("종료된 결정만 보관할 수 있습니다."));
                    d.archive(true, now);
                }
                case UNARCHIVE -> d.archive(false, now);
                case PERSONALIZATION_ON -> d.personalization(true, now);
                case PERSONALIZATION_OFF -> d.personalization(false, now);
                default -> throw new IllegalStateException();
            }
        }
        em.flush();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        org.slf4j.LoggerFactory.getLogger(DecisionRetentionService.class).info(
                                "product_event=decision_retention_changed decision_id={} action={} changed_at={}", id, request.action(), now);
                    }
                });
        return item(d);
    }

    private RetentionItem item(Decision d) {
        return new RetentionItem(d.id(), d.version(), d.title(), d.status(), d.archivedAt(), d.deletedAt(),
                d.deletedAt() == null ? null : d.deletedAt().plus(RETENTION_DAYS, ChronoUnit.DAYS),
                d.deletedAt() != null && Instant.now().isBefore(d.deletedAt().plus(RETENTION_DAYS, ChronoUnit.DAYS)), d.personalizationEnabled());
    }

    @Transactional
    public void purgeExpired(UUID id, Instant now) {
        var d = em.find(Decision.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null || d.deletedAt() == null || d.deletedAt().plus(RETENTION_DAYS, ChronoUnit.DAYS).isAfter(now)) return;
        em.createNativeQuery("delete from decisions where id = :id").setParameter("id", id).executeUpdate();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        org.slf4j.LoggerFactory.getLogger(DecisionRetentionService.class).info(
                                "product_event=decision_deleted decision_id={} deleted_at={}", id, now);
                    }
                });
    }
}
