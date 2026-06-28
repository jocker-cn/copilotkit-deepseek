package com.copilotkit.be.protocol;

import java.time.Instant;
import java.util.List;

public record ChatHistoryResponse(
        List<Thread> threads
) {
    public record Thread(
            String threadId,
            String title,
            List<Message> messages,
            Instant updatedAt
    ) {
    }

    public record Message(
            String id,
            String role,
            String content
    ) {
    }
}
