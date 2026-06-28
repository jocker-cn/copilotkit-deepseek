package com.copilotkit.be.web;

import com.copilotkit.be.protocol.ChatStreamRequest;
import com.copilotkit.be.service.DeepSeekStreamingChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/deepseek")
public class DeepSeekStreamController {

    private final DeepSeekStreamingChatService streamingChatService;

    public DeepSeekStreamController(DeepSeekStreamingChatService streamingChatService) {
        this.streamingChatService = streamingChatService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request) {
        return streamingChatService.stream(request);
    }
}
