package com.dogsout.server.notification;

import com.dogsout.server.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Delivers pushes through Expo's push API — no Firebase server SDK needed,
 * the Expo token identifies the device across iOS and Android.
 */
@Slf4j
@Service
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient restClient = RestClient.create();

    public boolean canReceive(User user) {
        return user.getExpoPushToken() != null
                && !user.getExpoPushToken().isBlank()
                && !Boolean.FALSE.equals(user.getNotificationsEnabled());
    }

    @Async
    public void send(User to, String title, String body, Map<String, Object> data) {
        if (!canReceive(to)) return;
        try {
            restClient.post()
                    .uri(EXPO_PUSH_URL)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "to", to.getExpoPushToken(),
                            "title", title,
                            "body", body,
                            "data", data,
                            "sound", "default"
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Notifications are best-effort; never fail the triggering request
            log.warn("Push to user {} failed: {}", to.getId(), e.getMessage());
        }
    }
}
