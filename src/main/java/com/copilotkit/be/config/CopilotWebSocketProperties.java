package com.copilotkit.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "copilot.websocket")
public class CopilotWebSocketProperties {

    private int maxTextMessageBufferSize = 256 * 1024;
    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

    public int getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    public void setMaxTextMessageBufferSize(int maxTextMessageBufferSize) {
        this.maxTextMessageBufferSize = maxTextMessageBufferSize;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
