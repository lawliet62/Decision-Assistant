package com.decisionassistant.notification;

import com.decisionassistant.decision.DecisionRepository;
import com.decisionassistant.identity.ApplicationUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class OutcomeReminderWorker {
    private final OutcomeReminderRepository reminders;
    private final DecisionRepository decisions;
    private final ApplicationUserRepository users;
    private final ReminderDeliveryPort delivery;
    private final ReminderProperties properties;
    private final String publicBaseUrl;
    private final Clock clock = Clock.systemUTC();
    public OutcomeReminderWorker(OutcomeReminderRepository reminders, DecisionRepository decisions,
                                 ApplicationUserRepository users, ReminderDeliveryPort delivery,
                                 ReminderProperties properties,
                                 @Value("${decision-assistant.auth.public-base-url}") String publicBaseUrl) {
        this.reminders = reminders; this.decisions = decisions; this.users = users; this.delivery = delivery;
        this.properties = properties; this.publicBaseUrl = publicBaseUrl;
    }
    @Scheduled(fixedDelayString = "${decision-assistant.reminders.poll-interval:60000}")
    @Transactional
    public void sendDue() {
        if (!properties.configured()) return;
        var now = Instant.now(clock);
        reminders.findTop10ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(OutcomeReminder.Status.PENDING, now).stream()
                .filter(item -> item.channel() == OutcomeReminder.Channel.EMAIL)
                .forEach(item -> send(item, now));
    }
    private void send(OutcomeReminder reminder, Instant now) {
        var decision = decisions.lockById(reminder.decisionId()).orElse(null);
        if (decision == null) return;
        // 부모 잠금 전에 선택한 알림도 정정·재검토가 먼저 확정되었다면 보내지 않는다.
        if (decision.status() != com.decisionassistant.decision.DecisionTypes.DecisionStatus.CHOSEN
                || reminders.pendingForDelivery(reminder.id(), now).isEmpty()
                || !java.util.Objects.equals(reminder.resolutionId(), decision.currentResolutionId())) return;
        try {
            var user = users.findById(decision.ownerUserId()).orElseThrow();
            if (!user.reminderEmailsEnabled()) { reminder.skip(); return; }
            var link = publicBaseUrl + "/decisions/" + decision.id() + "#outcome";
            reminder.sent(delivery.send(reminder.id(), user.email(), decision.title(), link), now);
        } catch (RuntimeException exception) {
            reminder.failed(exception.getMessage() == null ? "이메일 전송 실패" : exception.getMessage(), now);
        }
    }
}
