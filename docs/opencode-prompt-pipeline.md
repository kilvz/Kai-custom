# Opencode Prompt Pipeline — Comprehensive Analysis

> Repo: https://github.com/anomalyco/opencode (branch: `dev`)
> Time of analysis: June 2026
> Purpose: Compare opencode's prompt architecture against Kai-custom for rearchitecture planning

## Architectural Overview

Opencode has a **modular, layered prompt pipeline** — fundamentally different from Kai's monolithic `ChatSystemPromptBuilder`. There is no single function that builds "the prompt". Instead, the prompt is assembled from independent pieces at request time:

```
User types message
  → App UI (SolidJS) captures prompt text + file context + agent mentions
  → buildRequestParts() serializes into parts (text, file, agent, image)
  → Session.prompt() stores user message + schedules LLM call
  → Session.session-manager (opencode/src/session/) orchestrates:
      - Gets agent system prompt
      - Gets conversation history (post-compaction)
      - Gets context attachments (files, references)
      - Builds LLMRequest
      - Sends via LLMClient (native route OR AI SDK)
  → Protocol layer lowers LLMRequest to provider-native format
  → Transport sends HTTP request → LLM provider
```

## Key Source Files

| File | Purpose |
|------|---------|
| `packages/core/src/agent.ts` | Agent definition schema (`Info` class with `system`, `description`, `permissions`) |
| `packages/core/src/session.ts` | Session management (CRUD, messages, compaction) |
| `packages/core/src/session/prompt.ts` | Prompt schema (text + file attachments + agent mentions + references) |
| `packages/core/src/session/message.ts` | Message schema for conversation storage |
| `packages/core/src/session/projector.ts` | Session event projector (reduces events to state) |
| `packages/llm/src/llm.ts` | `LLM.request()`, `LLM.generate()`, `LLM.stream()` — entry points |
| `packages/llm/src/schema/messages.ts` | `LLMRequest`, `Message`, `SystemPart`, `ToolDefinition`, `ToolChoice` |
| `packages/llm/src/route/protocol.ts` | Protocol interface (body.from, body.schema, stream.step) |
| `packages/llm/src/route/client.ts` | `LLMClient.stream/generate` — request execution |
| `packages/llm/src/route/executor.ts` | Request executor (HTTP transport, error mapping) |
| `packages/llm/src/tool-runtime.ts` | Tool execution runtime (schema validation, dispatch, streaming) |
| `packages/opencode/src/session/llm.ts` | Session-owned LLM orchestration (AI SDK vs native routing) |
| `packages/opencode/src/session/llm/native-request.ts` | Lowering adapter: session data → LLMRequest |
| `packages/opencode/src/session/llm/native-runtime.ts` | Execution adapter: LLMClient.stream → opencode events |
| `packages/opencode/src/session/llm/ai-sdk.ts` | AI SDK compatibility bridge |
| `packages/app/src/context/prompt.tsx` | App-side prompt state (text, file context, agent mentions) |
| `packages/app/src/components/prompt-input/build-request-parts.ts` | Serializes prompt into request parts |
| `packages/app/src/context/server-sdk.tsx` | Server SDK client (SSE event stream from server) |

## Prompt Flow — Step by Step

### 1. User Input Assembly (App Side — SolidJS)

The user types text, attaches files, mentions agents. All managed in `prompt.tsx`:

```
Prompt = ContentPart[]
ContentPart = TextPart | FileAttachmentPart | AgentPart | ImageAttachmentPart
```

**`buildRequestParts()`** (`build-request-parts.ts`) serializes the prompt into the server API format:
- Text part: `{ type: "text", text: "..." }`
- File part: `{ type: "file", url: "file:///path", filename: "...", source: {...} }`
- Agent mention: `{ type: "agent", name: "..." }`
- Image: `{ type: "file", url: "data:...", mime: "...", filename: "..." }`

Context files (from sidebar) are also serialized. Duplicate file URLs are deduplicated. Comments on files produce synthetic text parts with `formatCommentNote()`.

### 2. Session Prompt Submission

The `Session.prompt()` method (`session.ts`) receives a `Prompt` object:
```
Prompt {
  text: string          (user's typed input)
  files?: FileAttachment[]
  agents?: AgentAttachment[]
  references?: ReferenceAttachment[]
}
```

This stores the user message and triggers the LLM orchestration.

### 3. LLM Orchestration (packages/opencode/src/session/llm.ts)

The session manager:
1. **Resolves the agent** → gets `AgentV2.Info` which has:
   - `system: string` — the user-defined system prompt
   - `description: string` — short description
   - `permissions: PermissionSchema.Ruleset`
   - `model: ModelV2.Ref`
   - `options: ProviderV2.Options`
2. **Gets conversation context** → `Session.context()` returns messages post-last-compaction
3. **Gets prompt attachments** → files, agents, references
4. **Builds `LLMRequest`** via `native-request.ts`:
   - `system` — from agent's system prompt + instructions.md
   - `messages` — conversation history + user message with file contents
   - `tools` — available tools (shell, file operations, MCP servers, etc.)
   - `toolChoice` — auto/none/required
   - `model` — selected model config
5. **Executes** via `LLMClient.stream()` (native route) or AI SDK fallback

### 4. LLMRequest Construction (llm.ts → schema/messages.ts)

`LLM.request()` creates a canonical `LLMRequest`:

```
LLMRequest {
  id?: string
  model: ModelSchema
  system: SystemPart[]          ← system prompt (array of { type: "text", text: "..." })
  messages: Message[]            ← conversation history
  tools: ToolDefinition[]        ← tool definitions
  toolChoice?: ToolChoice         ← auto/none/required/tool
  generation?: GenerationOptions  ← temperature, maxTokens, etc.
  providerOptions?: ProviderOptions
  http?: HttpOptions
  responseFormat?: ResponseFormat
  cache?: CachePolicy
  metadata?: Record<string, unknown>
}
```

**System prompt sources** (assembled in `native-request.ts`):
1. Agent's `system` field (user-configured in opencode.json)
2. Project `instructions.md` (user's per-project instructions)
3. System-level `AGENTS.md` instructions
4. Skill instructions (when a skill is active)

**Messages array** (built in `session.ts`):
1. Conversation history from database (post-compaction)
2. Current user message with expanded file contents
3. Context files are embedded as file parts with their content

### 5. Protocol Lowering (LLM Core)

The `Protocol` interface (`route/protocol.ts`) converts `LLMRequest` → provider-native format:

```
Protocol {
  body: {
    schema: Schema<Body>      — validates provider-native body
    from: (LLMRequest) => Body — builds provider-native body
  }
  stream: {
    event: Schema<Event, Frame> — streaming event decoder
    initial: (LLMRequest) => State
    step: (State, Event) => [State, LLMEvent[]]
    onHalt?: (State) => LLMEvent[]
  }
}
```

Protocols are provider-specific: `OpenAIChat.protocol`, `OpenAIResponses.protocol`, `AnthropicMessages.protocol`, `Gemini.protocol`, `BedrockConverse.protocol`.

A `Route` composes `Protocol + Endpoint + Auth + Framing`. Shared endpoints (DeepSeek, TogetherAI, etc.) reuse `OpenAIChat.protocol` with different route configs.

### 6. Tool Execution Runtime

The tool runtime (`tool-runtime.ts`) handles:
- Schema-validated tool dispatch (`parameters` Schema → typed input → execute → encode result)
- Streaming tool call accumulation (`tool-input-delta` events)
- Provider-defined tools (Anthropic `web_search`, OpenAI Responses `web_search_call`)
- Error recovery via `ToolFailure` → `tool-error` event
- Multi-step loops with `stopWhen` (e.g., `LLM.stepCountIs(10)`)

## Categories of Prompt Content

### What the AI Receives

**System prompt** (agent's `system` field + `instructions.md`):
- Agent personality/behavior definition
- Per-project instructions
- Tool usage rules (inferred from permissions)
- No fixed sections like Kai's Soul/Honesty/Memory

**Messages**:
- Conversation history (user ↔ assistant exchanges)
- File contents embedded as `user` messages with file parts
- Agent mentions → subagent routing
- Tool calls + results interleaved

**Tools**:
- Shell commands (permission-gated)
- File read/write (permission-gated)
- MCP server tools
- Search/glob tools
- Provider-defined tools (web search, code execution)
- No Memory/KG/Diary tools (opencode doesn't have a memory system like Kai)

### What the AI Does NOT Receive (vs Kai)

| Kai Feature | Opencode Equivalent |
|------------|-------------------|
| Soul text (persona definition) | Agent `system` field (user writes it) |
| Memory dumps (facts, preferences) | None (no persistent memory system) |
| Knowledge Graph | None |
| Email accounts | None (no email integration) |
| Scheduled tasks | None |
| Heartbeat automation | None |
| Structured learning rules | None |
| Prompt style (KAI vs ALT) | Single mode per agent config |
| Tool definitions in prompt | Added as `tools` array (separate from system) |

## Architectural Differences: Kai vs Opencode

| Aspect | Kai | Opencode |
|--------|-----|----------|
| **Prompt assembly** | Monolithic `ChatSystemPromptBuilder` | Modular `LLMRequest` with separate `system`/`messages`/`tools` |
| **System prompt** | Built dynamically from soul, memory, tools, rules | Static per agent + per-project instructions.md |
| **File context** | Baked into system prompt (file summaries) | Attached as separate message parts |
| **Memory system** | Embedded memory dumps + KG in prompt | None (no memory system) |
| **Tool definitions** | Embedded in system prompt text | OpenAPI-style `tools` array (JSON Schema) |
| **Personas** | KAI/ALT/CUSTOM prompt styles | One style per agent (user-defined `system`) |
| **Language** | Kotlin (Compose Multiplatform) | TypeScript (SolidJS frontend + Effect backend) |
| **Architecture** | Single-process mobile app | Client-server (desktop app ↔ local server) |
| **Session storage** | In-memory (chat log only) | SQLite database with compaction |
| **LLM core** | Custom OpenAI/Azure/Auth API calls | Effect Schema-based LLM core with Protocol/Route abstraction |
| **Tool execution** | ToolExecutor (imperative) | Effect-based tool runtime with Schema validation |
| **Streaming** | Custom SSE parser | Protocol-based state machine per provider |

## Key Insight for Rearchitecture

The fundamental difference is that Kai **bakes everything into the system prompt** (soul, memory, tool definitions, rules), while opencode **separates concerns**:
- System prompt = agent identity + instructions
- Messages = conversation + file context
- Tools = typed JSON Schema definitions
- Each piece processed independently by the provider

This means Kai's `ChatSystemPromptBuilder` could be refactored to:
1. Move tool definitions to `tools` array (OpenAI-compatible)
2. Keep system prompt for identity + rules only
3. Attach file context as message parts (when using OpenAI assistants)
4. Add optional memory dumps as system prompt additions

But Kai's architecture (mobile app, single-process, legacy API) constrains how much of opencode's approach is directly applicable.
