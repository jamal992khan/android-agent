package ai.openclaw.androidagent.core

import android.content.Context
import ai.openclaw.androidagent.models.Message
import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.tools.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core agent runtime - manages messages, tools, and LLM interaction
 */
class AgentCore private constructor(context: Context) {
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val tools = mutableMapOf<String, Tool>()
    private val llmManager = LLMManager(context)
    
    init {
        // Register built-in tools
        registerTool(TapTool())
        registerTool(SwipeTool())
        registerTool(TypeTextTool())
        registerTool(ScreenshotTool())
        registerTool(ShellTool())
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
            // Add user message
            addMessage(Message(content = userMessage, isUser = true))
            
            // Call LLM with conversation history and tool definitions
            val llmMessages = _messages.value.map { msg ->
                LLMManager.Message(content = msg.content, isUser = msg.isUser)
            }
            
            val toolDefinitions = tools.values.map { tool ->
                LLMManager.ToolDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters.mapValues { it.value.type }
                )
            }
            
            val response = llmManager.chat(llmMessages, toolDefinitions)
            
            // Execute any tool calls
            if (response.toolCalls.isNotEmpty()) {
                val toolResults = mutableListOf<String>()
                
                response.toolCalls.forEach { toolCall ->
                    val result = executeToolCall(toolCall)
                    toolResults.add(result)
                }
                
                // Add tool execution results
                val combinedResponse = if (response.text.isNotEmpty()) {
                    "${response.text}\n\n${toolResults.joinToString("\n")}"
                } else {
                    toolResults.joinToString("\n")
                }
                
                addMessage(Message(content = combinedResponse, isUser = false))
            } else {
                // Just text response
                addMessage(Message(content = response.text, isUser = false))
            }
            
        } catch (e: Exception) {
            addMessage(Message(
                content = "Error: ${e.message}",
                isUser = false
            ))
        } finally {
            _isProcessing.value = false
        }
    }
    
    private suspend fun executeToolCall(toolCall: LLMManager.ToolCall): String {
        val tool = tools[toolCall.tool] ?: return "❌ Tool '${toolCall.tool}' not found"
        
        val result = tool.execute(toolCall.params)
        return if (result.success) {
            "✅ ${tool.name}: ${result.data ?: "Success"}"
        } else {
            "❌ ${tool.name} failed: ${result.error}"
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
