package ai.openclaw.androidagent.core

import ai.openclaw.androidagent.models.Message
import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolCall
import ai.openclaw.androidagent.tools.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Core agent runtime - manages messages, tools, and LLM interaction
 */
class AgentCore private constructor() {
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val tools = mutableMapOf<String, Tool>()
    
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
    
    suspend fun sendMessage(userMessage: String) {
        if (_isProcessing.value) return
        
        _isProcessing.value = true
        
        try {
            // Add user message
            addMessage(Message(content = userMessage, isUser = true))
            
            // TODO: Call LLM with tools
            // For now, simple echo response
            val response = processWithLLM(userMessage)
            addMessage(Message(content = response, isUser = false))
            
        } catch (e: Exception) {
            addMessage(Message(
                content = "Error: ${e.message}",
                isUser = false
            ))
        } finally {
            _isProcessing.value = false
        }
    }
    
    private suspend fun processWithLLM(input: String): String {
        // TODO: Implement actual LLM call
        // This is a placeholder that demonstrates tool usage
        
        return when {
            input.contains("tap", ignoreCase = true) -> {
                "I can help you tap on the screen. Use the tap tool with coordinates."
            }
            input.contains("screenshot", ignoreCase = true) -> {
                "Taking a screenshot..."
            }
            else -> {
                "I'm a prototype Android agent. I can tap, swipe, type text, take screenshots, and run shell commands. What would you like me to do?"
            }
        }
    }
    
    private suspend fun executeToolCall(toolCall: ToolCall): String {
        val tool = tools[toolCall.name] ?: return "Tool '${toolCall.name}' not found"
        
        val params = mutableMapOf<String, Any>()
        toolCall.parameters.keys().forEach { key ->
            params[key] = toolCall.parameters.get(key)
        }
        
        val result = tool.execute(params)
        return if (result.success) {
            "Tool executed successfully: ${result.data}"
        } else {
            "Tool execution failed: ${result.error}"
        }
    }
    
    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }
    
    companion object {
        @Volatile
        private var instance: AgentCore? = null
        
        fun getInstance(): AgentCore {
            return instance ?: synchronized(this) {
                instance ?: AgentCore().also { instance = it }
            }
        }
    }
}
