package com.copilotkit.be.web;

import com.copilotkit.be.history.ChatHistoryMessage;
import com.copilotkit.be.history.ChatHistoryService;
import com.copilotkit.be.history.ChatThread;
import com.copilotkit.be.protocol.ChatHistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/copilot")
public class CopilotHistoryController {

    private final ChatHistoryService chatHistoryService;

    public CopilotHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/history")
    public ChatHistoryResponse history() {
        return toResponse(chatHistoryService.listThreads());
    }

    @PostMapping("/threads")
    public ChatHistoryResponse.Thread createThread() {
        return toThread(chatHistoryService.createThread());
    }

    private ChatHistoryResponse toResponse(List<ChatThread> threads) {
        return new ChatHistoryResponse(threads.stream().map(this::toThread).toList());
    }

    private ChatHistoryResponse.Thread toThread(ChatThread thread) {
        return new ChatHistoryResponse.Thread(
                thread.threadId(),
                thread.title(),
                toMessages(thread.messages()),
                thread.updatedAt()
        );
    }

    private List<ChatHistoryResponse.Message> toMessages(List<ChatHistoryMessage> messages) {
        List<ChatHistoryResponse.Message> responseMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatHistoryMessage message = messages.get(i);
            if (!"user".equals(message.role()) && !"assistant".equals(message.role())) continue;
            if (message.content() == null || message.content().isBlank()) continue;
            responseMessages.add(new ChatHistoryResponse.Message(
                    message.role() + "-" + i,
                    message.role(),
                    message.content()
            ));
        }
        return responseMessages;
    }
}
