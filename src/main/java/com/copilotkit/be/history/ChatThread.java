package com.copilotkit.be.history;

import java.time.Instant;
import java.util.List;

public record ChatThread(
        String threadId,
        String title,
        List<ChatHistoryMessage> messages,
        Instant createdAt,
        Instant updatedAt
) {
}
