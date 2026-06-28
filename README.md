# CopilotKit BE

Spring Boot backend for the CopilotKit UI socket adapter.

## Stack

- Java 21
- Spring Boot 4.1.x
- Spring AI 2.0.x
- DeepSeek OpenAI-compatible API
- WebSocket endpoint: `/ws/copilot`

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: copilotkit-be
  ai:
    model:
      chat: deepseek
    deepseek:
      api-key: your_deepseek_api_key
      base-url: https://api.deepseek.com
      chat:
        model: deepseek-v4-flash
```

The default model is `deepseek-v4-flash`. Change `spring.ai.deepseek.chat.model` to `deepseek-v4-pro` when needed.

## Debug logging

Enable Copilot debug logs in `src/main/resources/application.yml`:

```yaml
copilot:
  debug:
    enabled: true
```

When enabled, the backend logs:

- the raw WebSocket request payload from the UI;
- the parsed UI request used to build the LLM request;
- the final DeepSeek request payload;
- raw and parsed tool call arguments returned by the LLM.

## Model smoke test

Before connecting the WebSocket flow, test DeepSeek with:

```http
POST http://localhost:8080/api/deepseek/chat
Content-Type: application/json

{
  "message": "你好，简单介绍一下你自己"
}
```

Curl:

```bash
curl -X POST "http://localhost:8080/api/deepseek/chat" \
  -H "Content-Type: application/json; charset=utf-8" \
  --data-binary '{"message":"\u4f60\u597d\uff0c\u7b80\u5355\u4ecb\u7ecd\u4e00\u4e0b\u4f60\u81ea\u5df1"}'
```

## Tool call smoke test

This endpoint uses Spring AI `@Tool` declarations to send tool definitions to DeepSeek, disables automatic tool execution, and returns the model-requested tool calls.

```bash
curl -X POST "http://localhost:8080/api/deepseek/tool-call" \
  -H "Content-Type: application/json; charset=utf-8" \
  --data-binary '{"message":"\u5e2e\u6211\u52fe\u9009\u4eae\u70b9\u4ea7\u54c1"}'
```

## Streaming chat smoke test

This endpoint calls the DeepSeek OpenAI-compatible streaming API directly so the server can split:

- `reasoning_content` -> `thinking_*`
- `content` -> `streaming_*`
- `tool_calls` -> `function_call`

It also stores full messages in an in-memory history by `threadId` and sends the recent history with each request.

```bash
curl -N -X POST "http://localhost:8080/api/deepseek/chat/stream" \
  -H "Content-Type: application/json; charset=utf-8" \
  --data-binary '{
    "threadId": "thread-demo-001",
    "message": "\u5e2e\u6211\u52fe\u9009\u4eae\u70b9\u4ea7\u54c1",
    "context": {
      "selectedChecklistItems": [],
      "selectedReleaseId": "release-001"
    },
    "tools": [
      {
        "name": "set_checklist_item",
        "description": "\u52fe\u9009\u6216\u53d6\u6d88\u52fe\u9009\u53d1\u5e03\u6e05\u5355\u91cc\u7684\u6307\u5b9a\u9879\u76ee",
        "parameters": {
          "type": "object",
          "properties": {
            "label": {
              "type": "string",
              "description": "\u6e05\u5355\u9879\u540d\u79f0"
            },
            "checked": {
              "type": "boolean",
              "description": "\u662f\u5426\u52fe\u9009"
            }
          },
          "required": ["label", "checked"]
        }
      }
    ]
  }'
```

## Frontend URL

The UI socket URL should point to:

```text
ws://localhost:8080/ws/copilot
```

The server emits the same frontend-facing protocol currently consumed by the UI adapter:

```json
{ "event": "streaming", "message": { "id": "msg-1", "content": "..." } }
```

Tool calls use:

```json
{
  "event": "function_call",
  "message": {
    "name": "set_checklist_item",
    "arguments": { "label": "亮点产品", "checked": true }
  }
}
```
