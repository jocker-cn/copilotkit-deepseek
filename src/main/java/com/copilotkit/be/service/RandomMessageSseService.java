package com.copilotkit.be.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RandomMessageSseService {

    private static final List<String> MESSAGES = List.of(
            "The backend says hello.",
            "A random SSE event just arrived.",
            "This message was pushed over a persistent connection.",
            "No request was needed for this update.",
            "SSE is still connected."
    );

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        return emitter;
    }

    @Scheduled(fixedRate = 1_000)
    void publishRandomMessage() {
        RandomMessage message = new RandomMessage(
                MESSAGES.get(ThreadLocalRandom.current().nextInt(MESSAGES.size())),
                Instant.now().toString()
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("random-message").data(message));
            } catch (IOException | IllegalStateException exception) {
                emitters.remove(emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    public record RandomMessage(String content, String sentAt) {
    }
}
