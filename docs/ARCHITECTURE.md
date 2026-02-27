# Architecture

## Overview

Android Agent is structured as a standalone Android application that uses the Accessibility Service API for device control, with optional root enhancement.

## Layers

### 1. UI Layer (Jetpack Compose)

**Components:**
- `MainActivity.kt` - Entry point
- `ChatScreen.kt` - Main chat interface
- `SettingsScreen.kt` - Configuration (TODO)

**Responsibilities:**
- Render UI
- Handle user input
- Display messages
- Show system status

### 2. Agent Core

**Components:**
- `AgentCore.kt` - Main agent runtime
- `ToolRegistry.kt` - Manages available tools
- `SessionManager.kt` - Conversation state (TODO)
- `MemoryManager.kt` - Persistent storage (TODO)

**Responsibilities:**
- Process user messages
- Call LLM with tool definitions
- Parse tool calls from LLM responses
- Execute tools
- Manage conversation history

**Flow:**
```
User Input → AgentCore.sendMessage()
           → LLM API call (with tools)
           → Parse response
           → Execute tool calls
           → Format results
           → Send back to LLM (if needed)
           → Display final response
```

### 3. Control Layer

#### Accessibility Service

**File:** `services/AgentAccessibilityService.kt`

**Capabilities:**
- Read screen hierarchy (view tree)
- Perform gestures (tap, swipe, scroll)
- Type text into fields
- Monitor screen changes
- Listen to notifications (TODO)

**Limitations:**
- Requires user to enable in Settings
- Can't access some system UI elements
- Performance depends on target app's accessibility support

#### Root Module (Optional)

**File:** `tools/ShellTool.kt`

**Capabilities:**
- Execute any shell command as root
- Direct input injection via `/dev/input/event*`
- Install/uninstall APKs
- Modify system settings
- Access restricted files

**Limitations:**
- Requires rooted device
- Security risk if misused
- May break SafetyNet

### 4. Tool System

**Interface:** `models/Tool.kt`

Each tool implements:
```kotlin
interface Tool {
    val name: String
    val description: String
    val parameters: Map<String, ToolParameter>
    suspend fun execute(params: Map<String, Any>): ToolResult
}
```

**Built-in Tools:**

| Tool | Implementation | Dependencies |
|------|----------------|--------------|
| `tap` | `TapTool.kt` | Accessibility Service |
| `swipe` | `SwipeTool.kt` | Accessibility Service |
| `type_text` | `TypeTextTool.kt` | Accessibility Service |
| `screenshot` | `ScreenshotTool.kt` | Accessibility Service |
| `shell` | `ShellTool.kt` | libsuperuser (root) |

**Tool Execution:**
1. LLM returns function call with parameters
2. `AgentCore` looks up tool in registry
3. Parameters validated against tool schema
4. Tool's `execute()` method called
5. Result returned to LLM (or user)

### 5. LLM Integration

**Planned Implementations:**

1. **Local (Ollama)**
   - HTTP client to local Ollama server
   - Supports function calling
   - Fully offline

2. **Remote (OpenAI-compatible)**
   - Retrofit HTTP client
   - Bearer token authentication
   - Works with OpenRouter, OpenAI, Anthropic, etc.

3. **On-Device (Gemini Nano)**
   - Android AICore API
   - Lowest latency
   - Privacy-first

**Tool Call Format (OpenAI-compatible):**
```json
{
  "role": "assistant",
  "content": null,
  "tool_calls": [{
    "id": "call_xyz",
    "type": "function",
    "function": {
      "name": "tap",
      "arguments": "{\"x\": 500, \"y\": 1000}"
    }
  }]
}
```

## Data Flow

### Typical Agent Loop

```
┌─────────────┐
│ User Input  │
└──────┬──────┘
       ↓
┌──────────────────┐
│ AgentCore        │
│ - Add to history │
│ - Build prompt   │
└──────┬───────────┘
       ↓
┌─────────────────────┐
│ LLM API             │
│ - Send conversation │
│ - Include tools     │
└──────┬──────────────┘
       ↓
┌──────────────────────┐
│ Parse Response       │
│ - Text content?      │
│ - Tool calls?        │
└──────┬───────────────┘
       ↓
  ┌────┴────┐
  │ Tool?   │
  └─┬─────┬─┘
    │ Yes │ No
    ↓     ↓
┌───────┐ ┌──────────┐
│Execute│ │ Display  │
│Tool   │ │ Response │
└───┬───┘ └──────────┘
    ↓
┌────────────┐
│ Add result │
│ to history │
└──────┬─────┘
       ↓
  Loop again
  if needed
```

## Threading Model

- **Main Thread**: UI only (Compose)
- **IO Dispatcher**: Network calls (LLM API)
- **Default Dispatcher**: Tool execution
- **Accessibility Thread**: System manages accessibility callbacks

## State Management

**Current:**
- `StateFlow` for reactive UI updates
- In-memory message list

**Planned:**
- Room database for persistence
- WorkManager for background tasks
- DataStore for preferences

## Security Considerations

1. **Accessibility Service Risks**
   - Can read passwords if user types them
   - Can perform actions without user knowledge
   - Mitigation: Clear UI, audit logs, user confirmation for sensitive actions

2. **Root Risks**
   - Full device access
   - Can brick device if misused
   - Mitigation: Sandbox shell commands, whitelist/blacklist

3. **LLM Risks**
   - May hallucinate dangerous commands
   - Could be prompt-injected via screen content
   - Mitigation: Human-in-the-loop for destructive actions, safety filters

## Performance

**Optimization Strategies:**
- Lazy loading of screen hierarchy (only when needed)
- Debounce rapid tool calls
- Cache screen snapshots
- Compress conversation history before sending to LLM

## Future Architecture Changes

1. **Plugin System**
   - Hot-load tools from external packages
   - Skill marketplace (like ClawHub)

2. **Multi-Agent**
   - Spawn sub-agents for parallel tasks
   - Agent-to-agent communication

3. **Learning Layer**
   - Record user corrections
   - Fine-tune local model on user patterns

4. **Desktop Companion**
   - Sync with OpenClaw on PC
   - Cross-device orchestration
