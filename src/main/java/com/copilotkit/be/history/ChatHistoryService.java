package com.copilotkit.be.history;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ChatHistoryService {

    private static final int DEFAULT_RECENT_LIMIT = 10;

    private final ConcurrentMap<String, List<ChatHistoryMessage>> messagesByThreadId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ThreadMetadata> metadataByThreadId = new ConcurrentHashMap<>();

    public ChatThread createThread() {
        String threadId = "thread-" + UUID.randomUUID();
        Instant now = Instant.now();
        ThreadMetadata metadata = new ThreadMetadata("New chat", now, now);
        metadataByThreadId.put(threadId, metadata);
        messagesByThreadId.put(threadId, new ArrayList<>());
        return new ChatThread(threadId, metadata.title(), List.of(), metadata.createdAt(), metadata.updatedAt());
    }

    public List<ChatThread> listThreads() {
        if (metadataByThreadId.isEmpty()) {
            return List.of(createThread());
        }

        return metadataByThreadId.entrySet().stream()
                .map((entry) -> {
                    String threadId = entry.getKey();
                    ThreadMetadata metadata = entry.getValue();
                    List<ChatHistoryMessage> messages = messagesByThreadId.getOrDefault(threadId, List.of());
                    return new ChatThread(threadId, resolveTitle(metadata.title(), messages), new ArrayList<>(messages), metadata.createdAt(), metadata.updatedAt());
                })
                .sorted(Comparator.comparing(ChatThread::updatedAt).reversed())
                .toList();
    }

    public List<ChatHistoryMessage> getRecentMessages(String threadId) {
        return getRecentMessages(threadId, DEFAULT_RECENT_LIMIT);
    }

    public List<ChatHistoryMessage> getRecentMessages(String threadId, int limit) {
        List<ChatHistoryMessage> messages = messagesByThreadId.getOrDefault(threadId, List.of());
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public void append(String threadId, ChatHistoryMessage message) {
        messagesByThreadId.compute(threadId, (key, messages) -> {
            List<ChatHistoryMessage> nextMessages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
            nextMessages.add(message);
            return nextMessages;
        });
        metadataByThreadId.compute(threadId, (key, metadata) -> {
            Instant now = Instant.now();
            if (metadata == null) {
                return new ThreadMetadata(resolveTitle("New chat", messagesByThreadId.getOrDefault(threadId, List.of())), now, now);
            }
            return new ThreadMetadata(resolveTitle(metadata.title(), messagesByThreadId.getOrDefault(threadId, List.of())), metadata.createdAt(), now);
        });
    }

    private String resolveTitle(String currentTitle, List<ChatHistoryMessage> messages) {
        if (!"New chat".equals(currentTitle)) return currentTitle;
        return messages.stream()
                .filter((message) -> "user".equals(message.role()))
                .map(ChatHistoryMessage::content)
                .filter((content) -> content != null && !content.isBlank())
                .findFirst()
                .map(this::truncateTitle)
                .orElse(currentTitle);
    }

    private String truncateTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 24) return normalized;
        return normalized.substring(0, 24) + "...";
    }

    private record ThreadMetadata(
            String title,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
