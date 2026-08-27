package com.copilotkit.be.service;

import com.copilotkit.be.protocol.ToolCallTestRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeepSeekToolCallTestService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ReleaseUiToolDeclarations releaseUiToolDeclarations;

    public DeepSeekToolCallTestService(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.releaseUiToolDeclarations = new ReleaseUiToolDeclarations();
    }

    public String callTool(ToolCallTestRequest testRequest) {
        ChatClientResponse response = chatClient
                .prompt()
                .system("""
                        你是一个多功能助手。
                        当用户表达勾选、取消勾选清单项的意图时，优先调用工具 set_checklist_item。
                        不要假装已经操作界面，应该通过工具调用表达操作意图。
                        """)
                .user(testRequest.message())
                .tools(releaseUiToolDeclarations)
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .call()
                .chatClientResponse();

        ChatResponse chatResponse = response.chatResponse();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("mode", "spring-ai-user-controlled-tool-call");
        result.put("autoToolExecution", false);

        if (chatResponse == null || chatResponse.getResult() == null) {
            result.put("hasToolCalls", false);
            result.put("content", "");
            result.set("toolCalls", objectMapper.createArrayNode());
            return writeJson(result);
        }

        Generation generation = chatResponse.getResult();
        AssistantMessage assistantMessage = generation.getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

        result.put("hasToolCalls", chatResponse.hasToolCalls());
        result.put("content", assistantMessage.getText());
        result.put("reasoningContent", extractReasoningContent(assistantMessage));
        result.set("toolCalls", serializeToolCalls(toolCalls));
        result.set("assistantMetadata", objectMapper.valueToTree(assistantMessage.getMetadata()));
        return writeJson(result);
    }

    private ArrayNode serializeToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        ArrayNode toolCallNodes = objectMapper.createArrayNode();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            ObjectNode toolCallNode = objectMapper.createObjectNode();
            toolCallNode.put("id", toolCall.id());
            toolCallNode.put("type", toolCall.type());
            toolCallNode.put("name", toolCall.name());
            toolCallNode.put("arguments", toolCall.arguments());
            toolCallNode.set("parsedArguments", readJson(toolCall.arguments()));
            toolCallNodes.add(toolCallNode);
        }
        return toolCallNodes;
    }

    private String extractReasoningContent(AssistantMessage assistantMessage) {
        if (assistantMessage instanceof DeepSeekAssistantMessage deepSeekAssistantMessage) {
            return deepSeekAssistantMessage.getReasoningContent();
        }
        Object reasoningContent = assistantMessage.getMetadata().get("reasoning_content");
        return reasoningContent == null ? null : String.valueOf(reasoningContent);
    }

    private String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize DeepSeek tool call request.", exception);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse DeepSeek tool call response.", exception);
        }
    }

    private static class ReleaseUiToolDeclarations {

        @Tool(name = "set_checklist_item", description = "勾选或取消勾选发布清单里的指定项目。")
        public String setChecklistItem(
                @ToolParam(description = "清单项名称，例如：亮点产品、发布渠道、发布时间") String label,
                @ToolParam(description = "true 表示勾选，false 表示取消勾选") boolean checked
        ) {
            return "tool call should be executed by frontend";
        }
    }
}
