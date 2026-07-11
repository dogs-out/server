package com.dogsout.server.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotBlank @Size(max = 2000) String message
) {}
