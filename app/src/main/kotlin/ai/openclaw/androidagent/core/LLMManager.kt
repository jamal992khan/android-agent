package ai.openclaw.androidagent.core

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages LLM interactions - supports:
 * 1. Gemini Nano (on-device via AICore)
 * 2. Gemini Pro (cloud via API key)
 * 3. Custom endpoints (Ollama, OpenAI-compatible)
 */
class LLMManager(private val context: Context) {
    
    data class LLMConfig(
        val provider: Provider,
        val apiKey: String? = null,
        val endpoint: String? = null,
        val model: String? = null
    )
    
    enum class Provider {
        GEMINI_NANO,    // On-device
        GEMINI_PRO,     // Cloud
        CUSTOM          // Ollama, OpenAI, etc.
    }
    
    private var config: LLMConfig = LLMConfig(
        provider = Provider.GEMINI_NANO  // Default to on-device
    )
    
    fun configure(newConfig: LLMConfig) {
        config = newConfig
    }
    
    suspend fun chat(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse = withContext(Dispatchers.IO) {
        when (config.provider) {
            Provider.GEMINI_NANO -> chatWithGeminiNano(messages, tools)
            Provider.GEMINI_PRO -> chatWithGeminiPro(messages, tools)
            Provider.CUSTOM -> chatWithCustomEndpoint(messages, tools)
        }
    }
    
    private suspend fun chatWithGeminiNano(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse {
        return try {
            // Gemini Nano via AICore (on-device)
            val model = GenerativeModel(
                modelName = "gemini-nano",
                apiKey = "" // Not needed for on-device
            )
            
            val prompt = buildPrompt(messages, tools)
            val response = model.generateContent(prompt)
            
            parseGeminiResponse(response.text ?: "")
        } catch (e: Exception) {
            // Fallback to placeholder if Gemini Nano not available
            ChatResponse(
                text = "Gemini Nano not available on this device. Error: ${e.message}\n\n" +
                       "Please configure a cloud provider or custom endpoint in Settings.",
                toolCalls = emptyList()
            )
        }
    }
    
    private suspend fun chatWithGeminiPro(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse {
        return try {
            val apiKey = config.apiKey ?: throw IllegalStateException("API key required for Gemini Pro")
            
            val model = GenerativeModel(
                modelName = config.model ?: "gemini-1.5-flash",
                apiKey = apiKey
            )
            
            val prompt = buildPrompt(messages, tools)
            val response = model.generateContent(prompt)
            
            parseGeminiResponse(response.text ?: "")
        } catch (e: Exception) {
            ChatResponse(
                text = "Gemini Pro error: ${e.message}",
                toolCalls = emptyList()
            )
        }
    }
    
    private suspend fun chatWithCustomEndpoint(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse {
        // TODO: Implement Retrofit-based custom endpoint
        return ChatResponse(
            text = "Custom endpoint not yet implemented. Coming soon!",
            toolCalls = emptyList()
        )
    }
    
    private fun buildPrompt(messages: List<Message>, tools: List<ToolDefinition>): String {
        val sb = StringBuilder()
        
        // System prompt with tool definitions
        sb.appendLine("You are an AI assistant with access to the following tools:")
        tools.forEach { tool ->
            sb.appendLine("- ${tool.name}: ${tool.description}")
            sb.appendLine("  Parameters: ${tool.parameters.keys.joinToString()}")
        }
        sb.appendLine()
        sb.appendLine("To use a tool, respond with JSON in this format:")
        sb.appendLine("""{"tool": "tool_name", "params": {"param1": "value1"}}""")
        sb.appendLine()
        
        // Conversation history
        messages.forEach { msg ->
            val role = if (msg.isUser) "User" else "Assistant"
            sb.appendLine("$role: ${msg.content}")
        }
        
        return sb.toString()
    }
    
    private fun parseGeminiResponse(text: String): ChatResponse {
        // Try to extract JSON tool calls
        val toolCalls = mutableListOf<ToolCall>()
        
        // Look for JSON blocks
        val jsonPattern = Regex("""\{[^}]*"tool"[^}]*\}""")
        jsonPattern.findAll(text).forEach { match ->
            try {
                val json = JSONObject(match.value)
                val toolName = json.getString("tool")
                val params = mutableMapOf<String, Any>()
                
                if (json.has("params")) {
                    val paramsJson = json.getJSONObject("params")
                    paramsJson.keys().forEach { key ->
                        params[key] = paramsJson.get(key)
                    }
                }
                
                toolCalls.add(ToolCall(toolName, params))
            } catch (e: Exception) {
                // Skip invalid JSON
            }
        }
        
        // Clean text (remove JSON blocks)
        val cleanText = text.replace(jsonPattern, "").trim()
        
        return ChatResponse(
            text = cleanText.ifEmpty { "Tool executed" },
            toolCalls = toolCalls
        )
    }
    
    data class Message(
        val content: String,
        val isUser: Boolean
    )
    
    data class ToolDefinition(
        val name: String,
        val description: String,
        val parameters: Map<String, String>
    )
    
    data class ToolCall(
        val tool: String,
        val params: Map<String, Any>
    )
    
    data class ChatResponse(
        val text: String,
        val toolCalls: List<ToolCall>
    )
}
