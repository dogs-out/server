package com.dogsout.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // A user may be connected from several devices at once
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) return;
        sessionsByUser.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId == null) return;
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // App-level keepalive: RN's WebSocket has no ping frames
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    public boolean isOnline(Long userId) {
        return sessionsByUser.containsKey(userId);
    }

    public void sendToUser(Long userId, ChatSocketEvent event) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) return;
        TextMessage payload;
        try {
            payload = new TextMessage(objectMapper.writeValueAsString(event));
        } catch (IOException e) {
            log.warn("Could not serialize socket event", e);
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(payload);
                }
            } catch (IOException e) {
                log.debug("Dropping dead socket session for user {}", userId);
            }
        }
    }

    private Long userId(WebSocketSession session) {
        Object id = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        return id instanceof Long value ? value : null;
    }
}