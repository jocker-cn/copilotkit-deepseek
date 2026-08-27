package com.copilotkit.be.web;

import com.copilotkit.be.service.RandomMessageSseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
public class RandomMessageSseController {

    private final RandomMessageSseService randomMessageSseService;

    public RandomMessageSseController(RandomMessageSseService randomMessageSseService) {
        this.randomMessageSseService = randomMessageSseService;
    }

    @GetMapping(value = "/random", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return randomMessageSseService.subscribe();
    }
}
