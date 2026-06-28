package com.copilotkit.be.service;

import com.copilotkit.be.protocol.ClientRunRequest;
import com.copilotkit.be.protocol.ServerMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class CopilotChatService {

    private final ObjectMapper objectMapper;

    public CopilotChatService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void handleRun(WebSocketSession session, ClientRunRequest request) throws IOException {
        String userText = extractUserText(request).toLowerCase(Locale.ROOT);
        String messageId = "assistant-" + Instant.now().toEpochMilli();

        send(session, ServerMessage.runStarted());

        if (userText.contains("勾选") || userText.contains("check")) {
            boolean checked = !(userText.contains("取消") || userText.contains("uncheck"));
            send(session, ServerMessage.functionCall("set_checklist_item", Map.of(
                    "label", "亮点产品",
                    "checked", checked
            )));
        }

        send(session, ServerMessage.streamingStarted(messageId));
        send(session, ServerMessage.streaming(messageId, "DeepSeek 接入骨架已初始化。"));
        send(session, ServerMessage.streaming(messageId, "当前服务先按现有 socket 协议返回事件，"));
        send(session, ServerMessage.streaming(messageId, "下一步会把这里替换为 Spring AI DeepSeek streaming。"));
        send(session, ServerMessage.streamingCompleted(messageId));
        send(session, ServerMessage.completed());
    }

    private String extractUserText(ClientRunRequest request) {
        JsonNode input = request.input();
        if (input == null) return "";

        JsonNode messages = input.path("messages");
        if (!messages.isArray() || messages.isEmpty()) return input.toString();

        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode message = messages.get(i);
            if (!"user".equals(message.path("role").asText())) continue;
            JsonNode content = message.path("content");
            if (content.isTextual()) return content.asText();
            return content.toString();
        }

        return input.toString();
    }

    private void send(WebSocketSession session, ServerMessage message) throws IOException {
        if (!session.isOpen()) return;
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}
