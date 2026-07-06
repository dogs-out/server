package com.dogsout.server.chat;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long senderId,
        String content,
        Instant sentAt,
        boolean isRead
) {}
