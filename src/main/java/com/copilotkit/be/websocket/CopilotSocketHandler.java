package com.copilotkit.be.websocket;

import com.copilotkit.be.config.CopilotWebSocketProperties;
import com.copilotkit.be.protocol.AgUiRunMapper;
import com.copilotkit.be.protocol.ChatStreamRequest;
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
    private final CopilotWebSocketProperties websocketProperties;

    public CopilotSocketHandler(
            AgUiRunMapper agUiRunMapper,
            DeepSeekStreamingChatService chatService,
            @Value("${copilot.debug.enabled:false}") boolean debugEnabled,
            ObjectMapper objectMapper,
            CopilotWebSocketProperties websocketProperties
    ) {
        this.agUiRunMapper = agUiRunMapper;
        this.chatService = chatService;
        this.debugEnabled = debugEnabled;
        this.objectMapper = objectMapper;
        this.websocketProperties = websocketProperties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(websocketProperties.getMaxTextMessageBufferSize());
        log.info("[CopilotTrace] WebSocket connected. sessionId={}, remoteAddress={}, maxTextMessageBufferSize={}",
                session.getId(), session.getRemoteAddress(), session.getTextMessageSizeLimit());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (debugEnabled) {
            log.info("[CopilotDebug] UI raw WebSocket request payload: {}", message.getPayload());
        }

        ClientRunRequest request = objectMapper.readValue(message.getPayload(), ClientRunRequest.class);
        if (!"run".equals(request.event())) {
            log.warn("[CopilotTrace] Unsupported WebSocket event. sessionId={}, event={}",
                    session.getId(), request.event());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ServerMessage.error("Unsupported event: " + request.event()))));
            return;
        }

        ChatStreamRequest streamRequest = agUiRunMapper.toChatStreamRequest(request);
        log.info("[CopilotTrace] WebSocket run received. sessionId={}, threadId={}, messageLength={}, toolCount={}",
                session.getId(),
                streamRequest.threadId(),
                streamRequest.message() == null ? 0 : streamRequest.message().length(),
                streamRequest.tools() == null ? 0 : streamRequest.tools().size());
        chatService.streamToWebSocket(streamRequest, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[CopilotTrace] WebSocket closed. sessionId={}, code={}, reason={}",
                session.getId(), status.getCode(), status.getReason());
    }
}
