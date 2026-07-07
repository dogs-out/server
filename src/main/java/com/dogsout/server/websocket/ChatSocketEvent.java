package com.dogsout.server.websocket;

import com.dogsout.server.chat.MessageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSocketEvent(
        String type,          // NEW_MESSAGE | NEW_MATCH
        Long matchId,
        MessageResponse message
) {
    public static ChatSocketEvent newMessage(Long matchId, MessageResponse message) {
        return new ChatSocketEvent("NEW_MESSAGE", matchId, message);
    }

    public static ChatSocketEvent newMatch(Long matchId) {
        return new ChatSocketEvent("NEW_MATCH", matchId, null);
    }
}