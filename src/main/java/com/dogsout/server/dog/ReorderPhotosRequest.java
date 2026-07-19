package com.dogsout.server.dog;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderPhotosRequest(@NotEmpty List<Long> photoIds) {
}
