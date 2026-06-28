package com.copilotkit.be.websocket;

import com.copilotkit.be.protocol.AgUiRunMapper;
import com.copilotkit.be.protocol.ClientRunRequest;
import com.copilotkit.be.protocol.ServerMessage;
import com.copilotkit.be.service.DeepSeekStreamingChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class CopilotSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CopilotSocketHandler.class);

    private final AgUiRunMapper agUiRunMapper;
    private final DeepSeekStreamingChatService chatService;
    private final boolean debugEnabled;
    private final ObjectMapper objectMapper;

    public CopilotSocketHandler(
            AgUiRunMapper agUiRunMapper,
            DeepSeekStreamingChatService chatService,
            @Value("${copilot.debug.enabled:false}") boolean debugEnabled,
            ObjectMapper objectMapper
    ) {
        this.agUiRunMapper = agUiRunMapper;
        this.chatService = chatService;
        this.debugEnabled = debugEnabled;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Keep the socket open and reuse it for multiple chat runs.
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (debugEnabled) {
            log.info("[CopilotDebug] UI raw WebSocket request payload: {}", message.getPayload());
        }

        ClientRunRequest request = objectMapper.readValue(message.getPayload(), ClientRunRequest.class);
        if (!"run".equals(request.event())) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ServerMessage.error("Unsupported event: " + request.event()))));
            return;
        }

        chatService.streamToWebSocket(agUiRunMapper.toChatStreamRequest(request), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Session scoped resources should be released here when streaming DeepSeek is connected.
    }
}
