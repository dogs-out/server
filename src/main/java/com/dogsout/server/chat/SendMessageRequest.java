package com.dogsout.server.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 2000, message = "A message can be up to 2000 characters.") String content
) {}
