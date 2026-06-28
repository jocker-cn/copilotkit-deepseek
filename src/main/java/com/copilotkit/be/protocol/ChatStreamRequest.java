package com.copilotkit.be.protocol;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record ChatStreamRequest(
        @NotBlank String threadId,
        @NotBlank String message,
        Map<String, Object> context,
        List<UiToolDefinition> tools
) {
}
