package ai.openclaw.androidagent.core

import android.content.Context
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
 */
class AgentCore private constructor(context: Context) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val tools = mutableMapOf<String, Tool>()
    private val llmManager = LLMManager(context)

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

        // Web browsing tools
        registerTool(WebSearchTool())
        registerTool(WebFetchTool())
        registerTool(WebSummarizeTool())
    }

    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
    }

    fun configureLLM(config: LLMManager.LLMConfig) {
        llmManager.configure(config)
    }

    suspend fun sendMessage(userMessage: String) {
        if (_isProcessing.value) return

        _isProcessing.value = true

        try {
            // Add user message to history
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
            while (round < maxToolRounds) {
                round++

                val llmMessages = _messages.value.map { msg ->
                    LLMManager.Message(content = msg.content, isUser = msg.isUser)
                }

                val response = llmManager.chat(llmMessages, toolDefinitions)

                if (response.toolCalls.isEmpty()) {
                    // No tool calls — final text response
                    if (response.text.isNotEmpty()) {
                        addMessage(Message(content = response.text, isUser = false))
                    }
                    break
                }

                // Execute tool calls and collect results
                val toolResultLines = mutableListOf<String>()
                response.toolCalls.forEach { toolCall ->
                    val resultLine = executeToolCall(toolCall)
                    toolResultLines.add(resultLine)
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

                // Add the assistant turn (with tool results) so LLM sees them next iteration
                addMessage(Message(content = assistantContent, isUser = false))

                // If all tool calls succeeded and the LLM provided a final text, stop
                // (The LLM will have a chance next iteration to produce a conclusion)
            }

            if (round >= maxToolRounds) {
                addMessage(
                    Message(
                        content = "⚠️ Reached maximum tool-use rounds ($maxToolRounds). Stopping to prevent an infinite loop.",
                        isUser = false
                    )
                )
            }

        } catch (e: Exception) {
            addMessage(
                Message(
                    content = "❌ Error: ${e.message}",
                    isUser = false
                )
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
