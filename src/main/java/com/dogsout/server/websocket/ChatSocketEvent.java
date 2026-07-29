package com.dogsout.server.websocket;

import com.dogsout.server.chat.MessageResponse;
import com.dogsout.server.playdate.PlaydateDtos.PlaydateMessageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSocketEvent(
        String type,          // NEW_MESSAGE | NEW_MATCH | PLAYDATE_MESSAGE | PLAYDATE_UPDATED
        Long matchId,
        MessageResponse message,
        Long playdateId,
        PlaydateMessageResponse playdateMessage
) {
    public static ChatSocketEvent newMessage(Long matchId, MessageResponse message) {
        return new ChatSocketEvent("NEW_MESSAGE", matchId, message, null, null);
    }

    public static ChatSocketEvent newMatch(Long matchId) {
        return new ChatSocketEvent("NEW_MATCH", matchId, null, null, null);
    }

    public static ChatSocketEvent playdateMessage(Long playdateId, PlaydateMessageResponse message) {
        return new ChatSocketEvent("PLAYDATE_MESSAGE", null, null, playdateId, message);
    }

    // Emitted on join/leave/update/cancel so open detail screens refresh
    public static ChatSocketEvent playdateUpdated(Long playdateId) {
        return new ChatSocketEvent("PLAYDATE_UPDATED", null, null, playdateId, null);
    }
}
