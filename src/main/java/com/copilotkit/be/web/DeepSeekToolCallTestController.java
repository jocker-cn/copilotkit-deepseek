package com.copilotkit.be.web;

import com.copilotkit.be.protocol.ToolCallTestRequest;
import com.copilotkit.be.service.DeepSeekToolCallTestService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deepseek")
public class DeepSeekToolCallTestController {

    private final DeepSeekToolCallTestService toolCallTestService;

    public DeepSeekToolCallTestController(DeepSeekToolCallTestService toolCallTestService) {
        this.toolCallTestService = toolCallTestService;
    }

    @PostMapping(value = "/tool-call", produces = MediaType.APPLICATION_JSON_VALUE)
    public String toolCall(@Valid @RequestBody ToolCallTestRequest request) {
        return toolCallTestService.callTool(request);
    }
}
