package com.copilotkit.be.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerMessage(Boolean isCompleted, String event, Message message, String error) {

    public static ServerMessage runStarted() {
        return new ServerMessage(false, "run_started", null, null);
    }

    public static ServerMessage streamingStarted(String messageId) {
        return new ServerMessage(false, "streaming_started", new Message(messageId, null, null, null), null);
    }

    public static ServerMessage streaming(String messageId, String content) {
        return new ServerMessage(false, "streaming", new Message(messageId, content, null, null), null);
    }

    public static ServerMessage streamingCompleted(String messageId) {
        return new ServerMessage(false, "streaming_completed", new Message(messageId, null, null, null), null);
    }

    public static ServerMessage thinkingStarted(String messageId) {
        return new ServerMessage(false, "thinking_started", new Message(messageId, null, null, null), null);
    }

    public static ServerMessage thinkingDelta(String messageId, String content) {
        return new ServerMessage(false, "thinking_delta", new Message(messageId, content, null, null), null);
    }

    public static ServerMessage thinkingCompleted(String messageId) {
        return new ServerMessage(false, "thinking_completed", new Message(messageId, null, null, null), null);
    }

    public static ServerMessage functionCall(String name, Map<String, Object> arguments) {
        return new ServerMessage(false, "function_call", new Message(null, null, name, arguments), null);
    }

    public static ServerMessage completed() {
        return new ServerMessage(true, "completed", null, null);
    }

    public static ServerMessage error(String error) {
        return new ServerMessage(true, "error", null, error);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String id, String content, String name, Map<String, Object> arguments) {
    }
}
