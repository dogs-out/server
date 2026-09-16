package com.dogsout.server.sitter;

import jakarta.validation.constraints.NotNull;

public record AcceptSitterRequest(@NotNull Long sitterId) {}
