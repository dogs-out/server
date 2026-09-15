package com.dogsout.server.sitter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * @param dogIds which dogs need looking after; at least one, and they must be yours
 * @param note   optional, for the things a time and a dog do not say
 */
public record CreateSittingRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull List<Long> dogIds,
        @Size(max = 500) String note
) {}
