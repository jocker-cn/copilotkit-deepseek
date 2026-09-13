package com.copilotkit.be.service;

import com.copilotkit.be.history.ChatHistoryMessage;
import com.copilotkit.be.history.ChatHistoryService;
import com.copilotkit.be.protocol.ChatStreamRequest;
import com.copilotkit.be.protocol.ServerMessage;
import com.copilotkit.be.protocol.UiToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DeepSeekStreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekStreamingChatService.class);

    private static final String SYSTEM_PROMPT = """
            你是一个全面的能手。
            你可以根据用户意图请求调用前端 UI 工具，但不要假装已经直接操作界面。
            如果当前上下文已经提供了页面状态，优先基于页面状态回答。
            调用工具时必须严格遵守 tools 里声明的 JSON Schema。
            不要发明参数名；如果工具参数是 id/selected，就只能返回 id/selected，不要返回 label/checked。
            回答要简洁。
            """;

    private final ChatHistoryService chatHistoryService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final boolean debugEnabled;
    private final String model;
    private final AtomicInteger activeStreams = new AtomicInteger();

    public DeepSeekStreamingChatService(
            ChatHistoryService chatHistoryService,
            ObjectMapper objectMapper,
            @Value("${spring.ai.deepseek.api-key}") String apiKey,
            @Value("${spring.ai.deepseek.base-url}") String baseUrl,
            @Value("${copilot.debug.enabled:false}") boolean debugEnabled,
            @Value("${spring.ai.deepseek.chat.model}") String model
    ) {
        this.chatHistoryService = chatHistoryService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.debugEnabled = debugEnabled;
        this.model = model;
    }

    public SseEmitter stream(ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        String traceId = createTraceId();
        log.info("[CopilotTrace] Stream scheduled. traceId={}, transport=sse, threadId={}",
                traceId, request.threadId());
        Thread.startVirtualThread(() -> streamOnCurrentThread(request, new SseServerMessageSink(emitter), traceId));
        return emitter;
    }

    public void streamToWebSocket(ChatStreamRequest request, WebSocketSession session) {
        String traceId = createTraceId();
        log.info("[CopilotTrace] Stream scheduled. traceId={}, transport=websocket, sessionId={}, threadId={}",
                traceId, session.getId(), request.threadId());
        Thread.startVirtualThread(() -> streamOnCurrentThread(request, new WebSocketServerMessageSink(session), traceId));
    }

    private void streamOnCurrentThread(ChatStreamRequest request, ServerMessageSink sink, String traceId) {
        long runStartedAt = System.nanoTime();
        String assistantMessageId = "assistant-" + Instant.now().toEpochMilli();
        String reasoningMessageId = assistantMessageId + "-reasoning";
        int activeStreamCount = activeStreams.incrementAndGet();
        log.info("[CopilotTrace] Stream task started. traceId={}, threadId={}, virtualThread={}, activeStreams={}",
                traceId, request.threadId(), Thread.currentThread().isVirtual(), activeStreamCount);

        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder assistantContent = new StringBuilder();
        Map<Integer, StreamingToolCall> streamingToolCalls = new LinkedHashMap<>();
        StreamState streamState = new StreamState();

        try {
            log.info("[CopilotTrace] Loading chat history. traceId={}, threadId={}", traceId, request.threadId());
            List<ChatHistoryMessage> recentMessages = chatHistoryService.getRecentMessages(request.threadId());
            chatHistoryService.append(request.threadId(), ChatHistoryMessage.user(request.message()));
            log.info("[CopilotTrace] Chat history ready. traceId={}, threadId={}, historyCount={}",
                    traceId, request.threadId(), recentMessages.size());

            sink.send(ServerMessage.runStarted());
            log.info("[CopilotTrace] run_started sent. traceId={}, threadId={}", traceId, request.threadId());

            String requestJson = writeJson(createDeepSeekRequest(request, recentMessages));
            if (debugEnabled) {
                log.info("[CopilotDebug] LLM request payload: {}", writePrettyJson(readJson(requestJson)));
            }
            log.info("[CopilotTrace] LLM request sending. traceId={}, threadId={}, model={}, historyCount={}, requestBytes={}",
                    traceId,
                    request.threadId(),
                    model,
                    recentMessages.size(),
                    requestJson.getBytes(StandardCharsets.UTF_8).length);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            long httpStartedAt = System.nanoTime();
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            log.info("[CopilotTrace] LLM response headers received. traceId={}, threadId={}, status={}, elapsedMs={}",
                    traceId,
                    request.threadId(),
                    response.statusCode(),
                    elapsedMillis(httpStartedAt));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = readAll(response.body());
                log.warn("[CopilotTrace] LLM request rejected. traceId={}, threadId={}, status={}, responseBytes={}",
                        traceId,
                        request.threadId(),
                        response.statusCode(),
                        errorBody.getBytes(StandardCharsets.UTF_8).length);
                if (debugEnabled) {
                    log.info("[CopilotDebug] LLM error response: {}", errorBody);
                }
                sink.send(ServerMessage.error(errorBody));
                sink.complete();
                return;
            }

            readStream(response.body(), sink, assistantMessageId, reasoningMessageId, reasoningContent, assistantContent, streamingToolCalls, streamState, traceId, request.threadId());
            completeOpenSections(sink, assistantMessageId, reasoningMessageId, streamState);
            emitToolCalls(sink, streamingToolCalls, request);
            sink.send(ServerMessage.completed());
            log.info("[CopilotTrace] completed sent. traceId={}, threadId={}, reasoningChars={}, answerChars={}, toolCallCount={}, totalElapsedMs={}",
                    traceId,
                    request.threadId(),
                    reasoningContent.length(),
                    assistantContent.length(),
                    streamingToolCalls.size(),
                    elapsedMillis(runStartedAt));

            chatHistoryService.append(
                    request.threadId(),
                    ChatHistoryMessage.assistant(
                            assistantContent.toString(),
                            reasoningContent.toString(),
                            toHistoryToolCalls(streamingToolCalls, request)
                    )
            );
            sink.complete();
        } catch (Exception exception) {
            log.error("[CopilotTrace] Stream failed. traceId={}, threadId={}, elapsedMs={}",
                    traceId, request.threadId(), elapsedMillis(runStartedAt), exception);
            try {
                sink.send(ServerMessage.error(exception.getMessage()));
                log.info("[CopilotTrace] error sent. traceId={}, threadId={}", traceId, request.threadId());
            } catch (IOException ignored) {
                log.warn("[CopilotTrace] Failed to send error to client. traceId={}, threadId={}",
                        traceId, request.threadId(), ignored);
            }
            sink.completeWithError(exception);
        } finally {
            int remainingStreams = activeStreams.decrementAndGet();
            log.info("[CopilotTrace] Stream task finished. traceId={}, threadId={}, activeStreams={}, totalElapsedMs={}",
                    traceId, request.threadId(), remainingStreams, elapsedMillis(runStartedAt));
        }
    }

    private ObjectNode createDeepSeekRequest(ChatStreamRequest request, List<ChatHistoryMessage> recentMessages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        body.set("messages", createMessages(request, recentMessages));

        if (request.tools() != null && !request.tools().isEmpty()) {
            body.set("tools", createTools(request.tools()));
            body.put("tool_choice", "auto");
        }

        return body;
    }

    private ArrayNode createMessages(ChatStreamRequest request, List<ChatHistoryMessage> recentMessages) {
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(createMessage("system", SYSTEM_PROMPT));

        if (request.context() != null && !request.context().isEmpty()) {
            messages.add(createMessage("system", "当前页面上下文：\n" + writeJson(objectMapper.valueToTree(request.context()))));
        }

        for (ChatHistoryMessage historyMessage : recentMessages) {
            if ("user".equals(historyMessage.role())) {
                messages.add(createMessage("user", historyMessage.content()));
            } else if ("assistant".equals(historyMessage.role())) {
                String assistantHistory = buildAssistantHistoryContent(historyMessage);
                if (!assistantHistory.isBlank()) {
                    messages.add(createMessage("assistant", assistantHistory));
                }
            }
        }

        messages.add(createMessage("user", request.message()));
        return messages;
    }

    private ObjectNode createMessage(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private String buildAssistantHistoryContent(ChatHistoryMessage historyMessage) {
        StringBuilder content = new StringBuilder();
        if (historyMessage.content() != null && !historyMessage.content().isBlank()) {
            content.append(historyMessage.content());
        }
        if (historyMessage.toolCalls() != null && !historyMessage.toolCalls().isEmpty()) {
            content.append("\n历史工具调用：").append(writeJson(objectMapper.valueToTree(historyMessage.toolCalls())));
        }
        return content.toString();
    }

    private ArrayNode createTools(List<UiToolDefinition> toolDefinitions) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (UiToolDefinition toolDefinition : toolDefinitions) {
            ObjectNode function = objectMapper.createObjectNode();
            function.put("name", toolDefinition.name());
            function.put("description", toolDefinition.description());
            function.set("parameters", objectMapper.valueToTree(toolDefinition.parameters()));

            ObjectNode tool = objectMapper.createObjectNode();
            tool.put("type", "function");
            tool.set("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private void readStream(
            InputStream inputStream,
            ServerMessageSink sink,
            String assistantMessageId,
            String reasoningMessageId,
            StringBuilder reasoningContent,
            StringBuilder assistantContent,
            Map<Integer, StreamingToolCall> streamingToolCalls,
            StreamState streamState,
            String traceId,
            String threadId
    ) throws IOException {
        long streamStartedAt = System.nanoTime();
        int dataChunkCount = 0;
        boolean doneReceived = false;
        boolean firstDataLogged = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || !line.startsWith("data:")) continue;

                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    doneReceived = true;
                    break;
                }

                dataChunkCount += 1;
                if (!firstDataLogged) {
                    firstDataLogged = true;
                    log.info("[CopilotTrace] First LLM SSE data received. traceId={}, threadId={}, elapsedMs={}",
                            traceId, threadId, elapsedMillis(streamStartedAt));
                }

                JsonNode chunk = readJson(data);
                JsonNode delta = chunk.path("choices").path(0).path("delta");
                boolean hadThinking = streamState.thinkingStarted;
                boolean hadContent = streamState.contentStarted;
                handleReasoningDelta(sink, reasoningMessageId, delta, reasoningContent, streamState);
                handleContentDelta(sink, assistantMessageId, reasoningMessageId, delta, assistantContent, streamState);
                handleToolCallDelta(delta, streamingToolCalls);
                if (!hadThinking && streamState.thinkingStarted) {
                    log.info("[CopilotTrace] First thinking event forwarded. traceId={}, threadId={}", traceId, threadId);
                }
                if (!hadContent && streamState.contentStarted) {
                    log.info("[CopilotTrace] First answer event forwarded. traceId={}, threadId={}", traceId, threadId);
                }
            }
        }
        log.info("[CopilotTrace] LLM SSE stream ended. traceId={}, threadId={}, dataChunkCount={}, doneReceived={}, elapsedMs={}",
                traceId, threadId, dataChunkCount, doneReceived, elapsedMillis(streamStartedAt));
    }

    private String createTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private void handleReasoningDelta(
            ServerMessageSink sink,
            String assistantMessageId,
            JsonNode delta,
            StringBuilder reasoningContent,
            StreamState streamState
    ) throws IOException {
        JsonNode reasoningDelta = delta.path("reasoning_content");
        if (!reasoningDelta.isTextual() || reasoningDelta.asText().isEmpty()) return;

        if (!streamState.thinkingStarted) {
            streamState.thinkingStarted = true;
            sink.send(ServerMessage.thinkingStarted(assistantMessageId));
        }

        String content = reasoningDelta.asText();
        reasoningContent.append(content);
        sink.send(ServerMessage.thinkingDelta(assistantMessageId, content));
    }

    private void handleContentDelta(
            ServerMessageSink sink,
            String assistantMessageId,
            String reasoningMessageId,
            JsonNode delta,
            StringBuilder assistantContent,
            StreamState streamState
    ) throws IOException {
        JsonNode contentDelta = delta.path("content");
        if (!contentDelta.isTextual() || contentDelta.asText().isEmpty()) return;

        if (streamState.thinkingStarted && !streamState.thinkingCompleted) {
            streamState.thinkingCompleted = true;
            sink.send(ServerMessage.thinkingCompleted(reasoningMessageId));
        }

        if (!streamState.contentStarted) {
            streamState.contentStarted = true;
            sink.send(ServerMessage.streamingStarted(assistantMessageId));
        }

        String content = contentDelta.asText();
        assistantContent.append(content);
        sink.send(ServerMessage.streaming(assistantMessageId, content));
    }

    private void handleToolCallDelta(JsonNode delta, Map<Integer, StreamingToolCall> streamingToolCalls) {
        JsonNode toolCalls = delta.path("tool_calls");
        if (!toolCalls.isArray()) return;

        for (JsonNode toolCallDelta : toolCalls) {
            int index = toolCallDelta.path("index").asInt(streamingToolCalls.size());
            StreamingToolCall toolCall = streamingToolCalls.computeIfAbsent(index, ignored -> new StreamingToolCall());
            if (toolCallDelta.path("id").isTextual()) {
                toolCall.id = toolCallDelta.path("id").asText();
            }
            if (toolCallDelta.path("type").isTextual()) {
                toolCall.type = toolCallDelta.path("type").asText();
            }
            JsonNode function = toolCallDelta.path("function");
            if (function.path("name").isTextual()) {
                toolCall.name = function.path("name").asText();
            }
            if (function.path("arguments").isTextual()) {
                toolCall.arguments.append(function.path("arguments").asText());
            }
        }
    }

    private void completeOpenSections(ServerMessageSink sink, String assistantMessageId, String reasoningMessageId, StreamState streamState) throws IOException {
        if (streamState.thinkingStarted && !streamState.thinkingCompleted) {
            streamState.thinkingCompleted = true;
            sink.send(ServerMessage.thinkingCompleted(reasoningMessageId));
        }
        if (streamState.contentStarted) {
            sink.send(ServerMessage.streamingCompleted(assistantMessageId));
        }
    }

    private void emitToolCalls(ServerMessageSink sink, Map<Integer, StreamingToolCall> streamingToolCalls, ChatStreamRequest request) throws IOException {
        for (StreamingToolCall streamingToolCall : streamingToolCalls.values()) {
            if (streamingToolCall.name == null || streamingToolCall.name.isBlank()) continue;
            for (Map<String, Object> arguments : streamingToolCall.parsedArgumentObjects()) {
                if (debugEnabled) {
                    log.info("[CopilotDebug] LLM tool call arguments. name={}, rawArguments={}, parsedArguments={}",
                            streamingToolCall.name,
                            streamingToolCall.arguments,
                            writeDebugJson(arguments)
                    );
                }
                sink.send(ServerMessage.functionCall(streamingToolCall.name, arguments));
            }
        }
    }

    private List<Map<String, Object>> toHistoryToolCalls(Map<Integer, StreamingToolCall> streamingToolCalls, ChatStreamRequest request) {
        List<Map<String, Object>> historyToolCalls = new ArrayList<>();
        for (StreamingToolCall streamingToolCall : streamingToolCalls.values()) {
            if (streamingToolCall.name == null || streamingToolCall.name.isBlank()) continue;
            for (Map<String, Object> arguments : streamingToolCall.parsedArgumentObjects()) {
                Map<String, Object> toolCall = new LinkedHashMap<>();
                toolCall.put("id", streamingToolCall.id);
                toolCall.put("type", streamingToolCall.type);
                toolCall.put("name", streamingToolCall.name);
                toolCall.put("arguments", arguments);
                historyToolCalls.add(toolCall);
            }
        }
        return historyToolCalls;
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON.", exception);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse JSON.", exception);
        }
    }

    private String writePrettyJson(JsonNode jsonNode) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            return writeJson(jsonNode);
        }
    }

    private String writeDebugJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private String readAll(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private interface ServerMessageSink {
        void send(ServerMessage message) throws IOException;

        void complete();

        void completeWithError(Exception exception);
    }

    private class SseServerMessageSink implements ServerMessageSink {
        private final SseEmitter emitter;

        private SseServerMessageSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void send(ServerMessage message) throws IOException {
            emitter.send(SseEmitter.event()
                    .name(message.event())
                    .data(writeJson(objectMapper.valueToTree(message))));
        }

        @Override
        public void complete() {
            emitter.complete();
        }

        @Override
        public void completeWithError(Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private class WebSocketServerMessageSink implements ServerMessageSink {
        private final WebSocketSession session;

        private WebSocketServerMessageSink(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void send(ServerMessage message) throws IOException {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(writeJson(objectMapper.valueToTree(message))));
                }
            }
        }

        @Override
        public void complete() {
            // Keep the socket open for the next run.
        }

        @Override
        public void completeWithError(Exception exception) {
            // Keep the socket open; an error event is sent when possible.
        }
    }

    private static class StreamState {
        private boolean thinkingStarted;
        private boolean thinkingCompleted;
        private boolean contentStarted;
    }

    private class StreamingToolCall {
        private String id;
        private String type = "function";
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private List<Map<String, Object>> parsedArgumentObjects() {
            if (arguments.isEmpty()) {
                return List.of(Map.of());
            }
            String rawArguments = arguments.toString();
            try {
                JsonNode jsonNode = readJson(rawArguments);
                return List.of(objectMapper.convertValue(jsonNode, Map.class));
            } catch (RuntimeException ignored) {
                return readConcatenatedArgumentObjects(rawArguments);
            }
        }

        private List<Map<String, Object>> readConcatenatedArgumentObjects(String rawArguments) {
            try {
                MappingIterator<Map> iterator = objectMapper.readerFor(Map.class).readValues(rawArguments);
                List<Map<String, Object>> parsedArguments = new ArrayList<>();
                while (iterator.hasNext()) {
                    parsedArguments.add(new LinkedHashMap<>(iterator.next()));
                }
                return parsedArguments.isEmpty() ? List.of(Map.of("rawArguments", rawArguments)) : parsedArguments;
            } catch (IOException exception) {
                return List.of(Map.of("rawArguments", rawArguments));
            }
        }
    }
}
