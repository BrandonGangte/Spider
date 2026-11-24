# Spider

Spider is a local LLM harness built with Java, Spring Boot, LangChain4j, and Ollama.

## Requirements

- Java 21+
- Maven 3.6.3+
- Node.js 22.12+ and npm
- Ollama running locally with your Qwen model pulled

## Configure the Ollama model

The defaults expect Ollama at `http://localhost:11434` and a model named `qwen3.5:9b`. Set `OLLAMA_MODEL` to the exact name shown by `ollama list` if your quantized model uses a different tag.

The model selector in the top bar lists models installed in Ollama. Spider remembers the selected model in this browser and sends it with each chat request; if it is removed from Ollama, Spider falls back to the configured model or another installed model. Each model uses the same conversation ID and persisted chat memory.

Optional environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `qwen3.5:9b` | Local Ollama model tag |
| `OLLAMA_TEMPERATURE` | `0.2` | Sampling temperature |
| `OLLAMA_TIMEOUT` | `PT120S` | Model request timeout |
| `SPIDER_CHAT_MAX_MESSAGES` | `30` | Messages retained per conversation |
| `SPIDER_WORKSPACE_ROOT` | `./workspace` | Root directory available to Spider's read-only file tools |
| `SPIDER_DB_URL` | `jdbc:h2:file:./spider;DB_CLOSE_ON_EXIT=FALSE` | Local H2 database URL for durable chat memory |
| `SPIDER_DB_USER` | `sa` | Local database username |
| `SPIDER_DB_PASSWORD` | empty | Local database password |
| `SERVER_PORT` | `8080` | HTTP server port |

## Run in development

Start the backend in one terminal:

```powershell
mvn spring-boot:run
```

Start the React UI in another terminal:

```powershell
cd spider-ui
npm install
npm run dev
```

Open the Vite URL printed in the terminal (normally `http://localhost:5173`). Vite proxies `/api` and `/actuator` requests to Spring Boot at `http://localhost:8080`.

## Build a single Spring Boot app

```powershell
cd spider-ui
npm install
npm run build
cd ..
mvn spring-boot:run
```

Spring Boot then serves the built React app at `http://localhost:8080` along with the API. The build output in `spider-ui/dist` is copied into the application classpath by Maven.

## Chat API

Send `POST /api/chat` with a message. Omit `conversationId` to start a new conversation; reuse the returned ID to continue its persisted chat context.

```json
{
  "message": "What can you help me with?"
}
```

Response:

```json
{
  "conversationId": "generated-uuid",
  "answer": "..."
}
```

Use `GET /api/chat/status` to see whether Ollama is reachable and the configured model is present. `GET /api/chat/{conversationId}/activity` returns recent tool activity for a conversation. `GET /actuator/health` checks Spider itself. The UI keeps conversation titles and displayed messages in browser local storage, while LangChain4j's active context is persisted in the local H2 file so it survives a Spider restart.

## Attach files to a chat

Use the paperclip button in the chat composer to attach up to five files at once, or focus the message box and paste a PNG/JPEG image with Ctrl+V. Spider extracts text from text/code files, PDF, and Word, PowerPoint, and Excel documents, then includes that text as context in the chat. PNG and JPEG images are resized and sent as image content to Ollama; image understanding requires a vision-capable model. Attachments are limited to 8 MB each and 20 MB total, with at most three images. Extracted text is capped per file and per message. Uploaded files are processed in memory and are not written into the workspace. The browser stores attachment names in its chat display history, not the uploaded file bytes; attachment content sent to the model is part of LangChain4j's persisted chat context in H2.

## Agent tools

Spider can call a small allowlisted tool set when it needs one. Workspace tools are opt-in: Spider only uses them when you ask it to inspect or search local workspace files, and it will not use them for general questions or casual conversation.

- `calculate`: evaluates basic arithmetic expressions without executing code.
- `getCurrentDateTime`: reads the Spider server's current date and time.
- `listWorkspaceFiles`, `searchWorkspaceFiles`, and `readWorkspaceFile`: inspect text files under `SPIDER_WORKSPACE_ROOT`.
- `searchWeb` and `readWebPage`: search the public web and read public web pages or PDFs when you ask for web research, provide a link, or need current information. Replies should cite only URLs returned by these tools; the UI renders those links as clickable references.

LangChain4j handles the model/tool/result loop. Each model response is capped at six tool-calling round trips, with a maximum of six sequential tool calls per round trip. The UI polls the activity endpoint while a response is running and displays which tool Spider is using. Workspace tools only read files beneath the configured root; they reject path traversal and hidden or sensitive paths, cap file sizes and search results, and cannot write files or run shell commands.

## Initial shape

- `api`: HTTP contract and local Ollama readiness status
- `service`: LangChain4j AI Service, safe tools, H2 chat-memory store, and tool activity recorder
- `config`: Ollama and chat-memory configuration
- `spider-ui`: React/Vite chat interface

The current app provides local agentic chat with per-conversation memory and bounded tool calling. Retrieval and evaluation workflows remain future harness capabilities.
