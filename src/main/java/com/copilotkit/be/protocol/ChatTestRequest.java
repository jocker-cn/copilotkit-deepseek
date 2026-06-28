package com.copilotkit.be.protocol;

import jakarta.validation.constraints.NotBlank;

public record ChatTestRequest(@NotBlank String message) {
}
