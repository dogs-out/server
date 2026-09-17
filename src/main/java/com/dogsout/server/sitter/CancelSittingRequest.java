package com.dogsout.server.sitter;

import jakarta.validation.constraints.Size;

/** @param reason optional; the owner is told, so a word here is worth a lot */
public record CancelSittingRequest(@Size(max = 300) String reason) {}
