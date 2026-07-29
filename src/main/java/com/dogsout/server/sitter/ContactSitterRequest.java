package com.dogsout.server.sitter;

import jakarta.validation.constraints.NotNull;

public record ContactSitterRequest(@NotNull Long targetUserId) {}
