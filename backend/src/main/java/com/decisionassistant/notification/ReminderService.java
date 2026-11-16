package com.decisionassistant.notification;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.common.NotFoundException;
import com.decisionassistant.decision.DecisionRepository;
import com.decisionassistant.identity.ApplicationUserRepository;
import com.decisionassistant.identity.CurrentUserProvider;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReminderService {
    public record NotificationPreferenceResponse(boolean emailEnabled) {}
    public record UpdateNotificationPreferenceRequest(boolean emailEnabled) {}
    public record UpdateReminderRequest(@NotBlank String action, Instant dueAt) {}

    private final OutcomeReminderRepository reminders;
    private final DecisionRepository decisions;
    private final ApplicationUserRepository users;
    private final CurrentUserProvider currentUser;
    public ReminderService(OutcomeReminderRepository reminders, DecisionRepository decisions,
                           ApplicationUserRepository users, CurrentUserProvider currentUser) {
        this.reminders = reminders; this.decisions = decisions; this.users = users; this.currentUser = currentUser;
    }
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse preference() {
        var user = users.findById(currentUser.current().id()).orElseThrow();
        return new NotificationPreferenceResponse(user.reminderEmailsEnabled());
    }
    @Transactional
    public NotificationPreferenceResponse updatePreference(UpdateNotificationPreferenceRequest request) {
        var user = users.findById(currentUser.current().id()).orElseThrow();
        user.reminderEmailsEnabled(request.emailEnabled());
        return new NotificationPreferenceResponse(user.reminderEmailsEnabled());
    }
    @Transactional
    public void updateReminder(UUID decisionId, UUID reminderId, UpdateReminderRequest request) {
        var decision = decisions.lockByIdAndOwner(decisionId, currentUser.current().id())
                .orElseThrow(() -> new NotFoundException("Decision을 찾을 수 없습니다."));
        var reminder = reminders.findByIdAndDecisionId(reminderId, decisionId)
                .orElseThrow(() -> new NotFoundException("Reminder를 찾을 수 없습니다."));
        if ("SKIP".equalsIgnoreCase(request.action())) reminder.skip();
        else if ("RESCHEDULE".equalsIgnoreCase(request.action()) && request.dueAt() != null) {
            if (decision.status() != com.decisionassistant.decision.DecisionTypes.DecisionStatus.CHOSEN
                    || !java.util.Objects.equals(reminder.resolutionId(), decision.currentResolutionId()))
                throw new com.decisionassistant.common.ConflictException("현재 완료 기록의 회고 알림만 다시 예약할 수 있습니다.");
            reminder.reschedule(request.dueAt());
        }
        else throw new InvalidDecisionException(List.of("action은 SKIP 또는 dueAt이 있는 RESCHEDULE이어야 합니다."));
    }
}
