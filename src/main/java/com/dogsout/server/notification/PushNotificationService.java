package com.dogsout.server.notification;

import com.dogsout.server.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
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
            Map<?, ?> response = restClient.post()
                    .uri(EXPO_PUSH_URL)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "to", to.getExpoPushToken(),
                            "title", title,
                            "body", body,
                            "data", data,
                            "sound", "default",
                            // Android needs a channel or the notification is delivered
                            // silently; this is the one the client creates on launch.
                            "channelId", "default"
                    ))
                    .retrieve()
                    .body(Map.class);
            logTicketErrors(to, response);
        } catch (Exception e) {
            // Notifications are best-effort; never fail the triggering request
            log.warn("Push to user {} failed: {}", to.getId(), e.getMessage());
        }
    }

    /**
     * Expo answers 200 even when it could not deliver, putting the reason in a
     * per-message ticket. Throwing that response away — which this did — makes a
     * whole platform going dark completely invisible from the server: Android
     * pushes silently failed for every user and the logs were clean throughout.
     *
     * <p>The errors worth recognising are configuration, not transient. {@code
     * DeviceNotRegistered} means the token is stale and should stop being used.
     * The credential ones mean nobody on that platform is receiving anything.
     */
    private void logTicketErrors(User to, Map<?, ?> response) {
        if (response == null) return;
        Object payload = response.get("data");
        List<?> tickets = payload instanceof List<?> list ? list
                : payload instanceof Map<?, ?> single ? List.of(single)
                : List.of();

        for (Object ticket : tickets) {
            if (!(ticket instanceof Map<?, ?> t)) continue;
            if (!"error".equals(t.get("status"))) continue;

            Object details = t.get("details");
            Object code = details instanceof Map<?, ?> d ? d.get("error") : null;
            if ("DeviceNotRegistered".equals(code)) {
                log.info("Push token for user {} is no longer registered", to.getId());
            } else {
                log.warn("Push to user {} rejected by Expo: {} ({})", to.getId(), t.get("message"), code);
            }
        }

        if (response.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
            log.warn("Expo push request for user {} returned errors: {}", to.getId(), errors);
        }
    }
}
