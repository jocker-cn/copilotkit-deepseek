package com.copilotkit.be.protocol;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record UiToolDefinition(
        @NotBlank String name,
        @NotBlank String description,
        Map<String, Object> parameters
) {
}
