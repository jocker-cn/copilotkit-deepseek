package com.copilotkit.be.history;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatHistoryMessage(
        String role,
        String content,
        String reasoningContent,
        List<Map<String, Object>> toolCalls,
        Instant createdAt
) {
    public static ChatHistoryMessage user(String content) {
        return new ChatHistoryMessage("user", content, null, List.of(), Instant.now());
    }

    public static ChatHistoryMessage assistant(String content, String reasoningContent, List<Map<String, Object>> toolCalls) {
        return new ChatHistoryMessage("assistant", content, reasoningContent, toolCalls, Instant.now());
    }
}
