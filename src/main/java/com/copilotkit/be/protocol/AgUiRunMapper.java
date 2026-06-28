package com.copilotkit.be.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgUiRunMapper {

    private static final Logger log = LoggerFactory.getLogger(AgUiRunMapper.class);

    private final boolean debugEnabled;
    private final ObjectMapper objectMapper;

    public AgUiRunMapper(
            @Value("${copilot.debug.enabled:false}") boolean debugEnabled,
            ObjectMapper objectMapper
    ) {
        this.debugEnabled = debugEnabled;
        this.objectMapper = objectMapper;
    }

    public ChatStreamRequest toChatStreamRequest(ClientRunRequest request) {
        JsonNode input = request.input();
        String threadId = input.path("threadId").asText("default");
        String message = extractLatestUserMessage(input.path("messages"));
        Map<String, Object> context = extractContext(input.path("context"));
        List<UiToolDefinition> tools = extractTools(input.path("tools"));
        ChatStreamRequest chatStreamRequest = new ChatStreamRequest(threadId, message, context, tools);
        if (debugEnabled) {
            log.info("[CopilotDebug] Parsed UI request for LLM: {}", writeDebugJson(chatStreamRequest));
        }
        return chatStreamRequest;
    }

    private String writeDebugJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private String extractLatestUserMessage(JsonNode messages) {
        if (!messages.isArray()) return "";

        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode message = messages.get(i);
            if (!"user".equals(message.path("role").asText())) continue;
            return extractContent(message.path("content"));
        }

        return "";
    }

    private String extractContent(JsonNode content) {
        if (content.isTextual()) return content.asText();
        if (!content.isArray()) return content.toString();

        StringBuilder text = new StringBuilder();
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asText())) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString();
    }

    private Map<String, Object> extractContext(JsonNode contexts) {
        Map<String, Object> mergedContext = new LinkedHashMap<>();
        if (!contexts.isArray()) return mergedContext;

        for (JsonNode context : contexts) {
            String description = context.path("description").asText("");
            Object parsedValue = parseContextValue(context.path("value"));
            if (!description.isBlank()) {
                mergedContext.put(description, parsedValue);
            } else if (parsedValue instanceof Map<?, ?> parsedMap) {
                for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                    mergedContext.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        return mergedContext;
    }

    private Object parseContextValue(JsonNode value) {
        if (value.isTextual()) {
            String text = value.asText();
            try {
                return objectMapper.readValue(text, Object.class);
            } catch (JsonProcessingException ignored) {
                return text;
            }
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private List<UiToolDefinition> extractTools(JsonNode toolsNode) {
        List<UiToolDefinition> tools = new ArrayList<>();
        if (!toolsNode.isArray()) return tools;

        for (JsonNode toolNode : toolsNode) {
            String name = toolNode.path("name").asText("");
            String description = toolNode.path("description").asText("");
            if (name.isBlank() || description.isBlank()) continue;

            Map<String, Object> parameters = objectMapper.convertValue(toolNode.path("parameters"), Map.class);
            tools.add(new UiToolDefinition(name, description, parameters));
        }

        return tools;
    }
}
