package com.copilotkit.be.config;

import com.copilotkit.be.websocket.CopilotSocketHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(CopilotWebSocketProperties.class)
public class WebSocketConfig implements WebSocketConfigurer {

    private final CopilotSocketHandler copilotSocketHandler;
    private final CopilotWebSocketProperties properties;

    public WebSocketConfig(CopilotSocketHandler copilotSocketHandler, CopilotWebSocketProperties properties) {
        this.copilotSocketHandler = copilotSocketHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(copilotSocketHandler, "/ws/copilot")
                .setAllowedOrigins(properties.getAllowedOrigins().toArray(String[]::new));
    }
}
