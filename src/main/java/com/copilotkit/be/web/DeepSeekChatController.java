package com.copilotkit.be.web;

import com.copilotkit.be.protocol.ChatTestRequest;
import com.copilotkit.be.protocol.ChatTestResponse;
import com.copilotkit.be.service.DeepSeekChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deepseek")
public class DeepSeekChatController {

    private final DeepSeekChatService deepSeekChatService;

    public DeepSeekChatController(DeepSeekChatService deepSeekChatService) {
        this.deepSeekChatService = deepSeekChatService;
    }

    @PostMapping("/chat")
    public ChatTestResponse chat(@Valid @RequestBody ChatTestRequest request) {
        return new ChatTestResponse(deepSeekChatService.chat(request.message()));
    }
}
