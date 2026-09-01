package com.decisionassistant.notification;

import java.util.UUID;

public interface ReminderDeliveryPort {
    String send(UUID reminderId, String email, String decisionTitle, String link);
}
