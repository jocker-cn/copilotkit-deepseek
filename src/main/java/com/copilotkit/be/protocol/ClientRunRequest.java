package com.copilotkit.be.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public record ClientRunRequest(String event, JsonNode input) {
}
