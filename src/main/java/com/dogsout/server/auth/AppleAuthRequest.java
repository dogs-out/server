package com.dogsout.server.auth;

import jakarta.validation.constraints.NotBlank;

public record AppleAuthRequest(@NotBlank String identityToken) {}