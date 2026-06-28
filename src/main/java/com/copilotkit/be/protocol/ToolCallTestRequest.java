package com.copilotkit.be.protocol;

import jakarta.validation.constraints.NotBlank;

public record ToolCallTestRequest(
        @NotBlank String message
) {
}
