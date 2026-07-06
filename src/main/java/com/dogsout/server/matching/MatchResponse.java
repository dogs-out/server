package com.dogsout.server.matching;

import java.time.Instant;

public record MatchResponse(
        Long matchId,
        Long otherUserId,
        String otherUserName,
        String otherUserProfilePicture,
        Instant matchedAt,
        String lastMessageContent,
        Instant lastMessageSentAt,
        Long lastMessageSenderId,
        long unreadCount
) {}
