package com.dogsout.server.chat;

import java.time.Instant;

/**
 * @param sittingRequestId set when the message is a sitter's offer on a sitting
 *                         request; the owner's side renders those with an Accept
 *                         button rather than as plain text. Null for ordinary messages.
 */
public record MessageResponse(
        Long id,
        Long senderId,
        String content,
        Instant sentAt,
        boolean isRead,
        Long sittingRequestId
) {}
