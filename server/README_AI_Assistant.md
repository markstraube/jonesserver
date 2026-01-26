# AI Assistant API Documentation

## Overview
This API provides an interface for users to interact with an AI assistant for explanation and analysis tasks within the financial application.

## Base URL
`/api/assistant/`

## Configuration
Add the following configuration to your `application.yml` or `application.properties`:

```yaml
ai:
  assistant:
    context:
      time-window-minutes: 60
      max-size-kb: 20
    llm:
      provider: openai # or anthropic
      timeout-seconds: 30
      api-key: YOUR_OPENAI_API_KEY
      model: gpt-4
      temperature: 0.7
```

## Endpoints

### 1. Explain Concept
**URL**: `POST /api/assistant/explain`
**Content-Type**: `application/json`
**Response**: Server-Sent Events (SSE)

**Request Body**:
```json
{
  "question": "Was ist der RSI?",
  "sessionId": "optional-uuid"
}
```

**Response Event**:
```json
{
  "type": "chunk",
  "content": "Der Relative Strength Index (RSI)...",
  "contentType": "markdown",
  "metadata": null
}
```

### 2. Analyze Data
**URL**: `POST /api/assistant/analyze`
**Content-Type**: `application/json`
**Response**: Server-Sent Events (SSE)

**Request Body**:
```json
{
  "question": "Analysiere die Apple Aktie.",
  "symbol": "AAPL",
  "timeRange": "1M",
  "sessionId": "optional-uuid"
}
```

### 3. Get Chat History
**URL**: `GET /api/assistant/history`
**Query Parameters**:
- `chat`: Type of chat session (`explain` or `analyze`)

**Response**: JSON List of session summaries sorted by timestamp descending.

**Response Body**:
```json
[
  {
    "timestamp": "2023-10-27T14:30:00Z",
    "title": "Erkläre mir den MACD",
    "session-id": "550e8400-e29b-41d4-a716-446655440000",
    "file-name": "chat/explain/550e8400-e29b-41d4-a716-446655440000.json"
  }
]
```

## Implementation Details
- **Architecture**: Spring WebFlux for non-blocking SSE streaming.
- **Components**:
  - `AIAssistantController`: REST endpoints.
  - `AIAssistantService`: Orchestrates data fetching and LLM calls.
  - `ContextService`: Manages conversation history in `UserPrefsRepo`.
  - `LLMService`: Handles communication with LLM providers.
- **Authentication**: Uses existing JWT authentication.
- **Providers**: Currently supports OpenAI, extensible for Anthropic.

## Data Flow
1. User sends request.
2. Context loaded from database.
3. Relevant financial data fetched (for analyze requests).
4. Prompt constructed with system instructions, context, and data.
5. Request sent to LLM.
6. Response streamed back to user via SSE.
7. Full response saved to context.
