package com.dogsout.server.sitter;

import java.time.Instant;

/**
 * A sitter's cancellation record, so the app can warn before a penalty rather
 * than after it.
 *
 * @param lateCancellations late cancellations inside the rolling year
 * @param strikesAllowed    how many are permitted before the penalty applies
 * @param blockedUntil      null unless a penalty is currently running
 */
public record SitterStandingResponse(
        long lateCancellations,
        int strikesAllowed,
        long lateHours,
        Instant blockedUntil
) {}
