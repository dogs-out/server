package com.dogsout.server.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s])[^\\s]{8,50}$",
                message = "Password must be 8-50 characters with uppercase, lowercase, digit, special character, and no spaces"
        ) String newPassword
) {}