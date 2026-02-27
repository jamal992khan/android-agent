package ai.openclaw.androidagent.core

import android.content.Context
import ai.openclaw.androidagent.memory.MemoryManager
import ai.openclaw.androidagent.models.Message
import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.tools.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core agent runtime - manages messages, tools, and LLM interaction.
 *
 * Supports multi-turn tool use: after executing tool calls the results are fed
 * back into the conversation so the LLM can reason over them and call more tools.
 *
 * Also integrates with [MemoryManager] to:
 * - Inject relevant past experiences and learned skills before each LLM call.
 * - Persist every interaction so the agent improves over time.
 */
class AgentCore private constructor(context: Context) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val tools = mutableMapOf<String, Tool>()
    private val llmManager = LLMManager(context)

    /** On-device memory — injected before every LLM call */
    private val memoryManager = MemoryManager.getInstance(context)

    /** Maximum number of agentic loops before we stop to avoid infinite loops */
    private val maxToolRounds = 8

    init {
        // Register built-in tools
        registerTool(TapTool())
        registerTool(SwipeTool())
        registerTool(TypeTextTool())
        registerTool(ScreenshotTool())
        registerTool(ShellTool())

        // Advanced tools
        registerTool(FindElementTool())
        registerTool(NavigationTool())
        registerTool(LaunchAppTool(context))
        registerTool(GetCurrentAppTool(context))
        registerTool(ScreenshotImageTool(context))
        registerTool(ScrollTool())
        registerTool(ClipboardTool(context))
        registerTool(NotificationTool())
        registerTool(LongPressTool())
        registerTool(WaitTool())
        registerTool(ClickElementTool())
    }

    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
    }

    fun configureLLM(config: LLMManager.LLMConfig) {
        llmManager.configure(config)
    }

    /** Expose the LLMManager for UI access (e.g., LlamaEngine for model status). */
    fun getLLMManager(): LLMManager = llmManager

    suspend fun sendMessage(userMessage: String) {
        if (_isProcessing.value) return

        _isProcessing.value = true

        // Track which tools were actually used and whether the exchange succeeded
        val usedTools = mutableListOf<String>()
        var exchangeSucceeded = true

        try {
            // ── Memory: retrieve relevant context ─────────────────────────────
            val memoryContext = memoryManager.getRelevantContext(userMessage)

            // Build the enriched user message with memory prefix (injected as
            // a hidden system note; it is NOT shown in the chat UI)
            val enrichedUserMessage = if (memoryContext.isNotBlank()) {
                "Relevant memories:\n$memoryContext\n\n---\nUser: $userMessage"
            } else {
                userMessage
            }

            // Add the *original* user message to the visible chat history
            addMessage(Message(content = userMessage, isUser = true))

            val toolDefinitions = tools.values.map { tool ->
                LLMManager.ToolDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters.mapValues { it.value.type }
                )
            }

            // Agentic loop: LLM calls tools, we execute, feed results back, repeat
            var round = 0
            var lastAgentResponse = ""

            while (round < maxToolRounds) {
                round++

                // For the first round, substitute the enriched message so the LLM
                // sees the memory context.  Subsequent rounds use the normal history.
                val llmMessages = if (round == 1) {
                    val history = _messages.value.dropLast(1)   // exclude the user msg we just added
                    history.map { msg ->
                        LLMManager.Message(content = msg.content, isUser = msg.isUser)
                    } + LLMManager.Message(content = enrichedUserMessage, isUser = true)
                } else {
                    _messages.value.map { msg ->
                        LLMManager.Message(content = msg.content, isUser = msg.isUser)
                    }
                }

                val response = llmManager.chat(llmMessages, toolDefinitions)

                if (response.toolCalls.isEmpty()) {
                    // No tool calls — final text response
                    if (response.text.isNotEmpty()) {
                        lastAgentResponse = response.text
                        addMessage(Message(content = response.text, isUser = false))
                    }
                    break
                }

                // Execute tool calls and collect results
                val toolResultLines = mutableListOf<String>()
                response.toolCalls.forEach { toolCall ->
                    usedTools.add(toolCall.tool)
                    val resultLine = executeToolCall(toolCall)
                    toolResultLines.add(resultLine)
                    // If any tool call failed, mark the exchange as failed
                    if (resultLine.startsWith("❌")) exchangeSucceeded = false
                }

                // Build assistant message that shows both the LLM text and tool results
                val toolResultBlock = toolResultLines.joinToString("\n")
                val assistantContent = buildString {
                    if (response.text.isNotEmpty()) {
                        appendLine(response.text)
                        appendLine()
                    }
                    appendLine("**Tool results:**")
                    appendLine(toolResultBlock)
                }.trimEnd()

                lastAgentResponse = assistantContent

                // Add the assistant turn (with tool results) so LLM sees them next iteration
                addMessage(Message(content = assistantContent, isUser = false))

                // If all tool calls succeeded and the LLM provided a final text, stop
                // (The LLM will have a chance next iteration to produce a conclusion)
            }

            if (round >= maxToolRounds) {
                val limitMsg = "⚠️ Reached maximum tool-use rounds ($maxToolRounds). Stopping to prevent an infinite loop."
                lastAgentResponse = limitMsg
                exchangeSucceeded = false
                addMessage(Message(content = limitMsg, isUser = false))
            }

            // ── Memory: persist the interaction ───────────────────────────────
            if (lastAgentResponse.isNotEmpty()) {
                memoryManager.remember(
                    userMessage = userMessage,
                    agentResponse = lastAgentResponse,
                    toolsUsed = usedTools,
                    success = exchangeSucceeded
                )
            }

        } catch (e: Exception) {
            exchangeSucceeded = false
            val errMsg = "❌ Error: ${e.message}"
            addMessage(Message(content = errMsg, isUser = false))

            // Still persist the failed interaction so the self-improvement loop
            // can learn from it
            memoryManager.remember(
                userMessage = userMessage,
                agentResponse = errMsg,
                toolsUsed = usedTools,
                success = false
            )
        } finally {
            _isProcessing.value = false
        }
    }

    private suspend fun executeToolCall(toolCall: LLMManager.ToolCall): String {
        val tool = tools[toolCall.tool]
            ?: return "❌ Tool '${toolCall.tool}' not found. Available: ${tools.keys.joinToString()}"

        return try {
            val result = tool.execute(toolCall.params)
            if (result.success) {
                "✅ ${tool.name}: ${result.data ?: "Success"}"
            } else {
                "❌ ${tool.name} failed: ${result.error}"
            }
        } catch (e: Exception) {
            "❌ ${tool.name} threw an exception: ${e.message}"
        }
    }

    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    companion object {
        @Volatile
        private var instance: AgentCore? = null

        fun getInstance(context: Context): AgentCore {
            return instance ?: synchronized(this) {
                instance ?: AgentCore(context.applicationContext).also { instance = it }
            }
        }
    }
}
