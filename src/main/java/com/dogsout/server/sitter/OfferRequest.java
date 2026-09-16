package com.dogsout.server.sitter;

import jakarta.validation.constraints.Size;

/** @param message what the sitter wants to say with the offer; optional */
public record OfferRequest(@Size(max = 500) String message) {}
