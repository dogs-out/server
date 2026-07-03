package com.dogsout.server.moderation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @NotBlank @Size(max = 100) String reason,
        @Size(max = 2000) String message
) {}