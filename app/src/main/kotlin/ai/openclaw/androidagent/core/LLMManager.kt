package ai.openclaw.androidagent.core

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages LLM interactions - supports:
 * 1. Gemini Nano (on-device via Android AICore / ML Kit GenAI)
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
        GEMINI_NANO,    // On-device (graceful fallback if unavailable)
        GEMINI_PRO,     // Cloud
        CUSTOM          // Ollama, OpenAI, etc.
    }

    private var config: LLMConfig = LLMConfig(
        provider = Provider.GEMINI_NANO  // Default: try on-device first (fast, private, free!)
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

    /**
     * Gemini Nano via ML Kit GenAI Prompt API (on-device, requires Android AICore).
     *
     * Availability:
     *  - Pixel 8+ running Android 14+ with Google Play and AICore installed
     *  - Other Android 13+ devices that have received the AICore update
     *
     * Gracefully handles:
     *  - Model not available on device
     *  - AICore not installed / not supported
     *  - Model still downloading
     */
    private suspend fun chatWithGeminiNano(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse {
        val nanoResult = runCatching {
            // Get the generative model client
            val model = Generation.getClient()

            // Check if Gemini Nano is available on this device
            val status = model.checkStatus()

            when (status) {
                FeatureStatus.AVAILABLE -> {
                    val prompt = buildPrompt(messages, tools)
                    val response = model.generateContent(prompt)
                    val text = response.candidates.firstOrNull()?.text ?: ""
                    parseResponse(text)
                }
                FeatureStatus.UNAVAILABLE -> null // device unsupported, fall through
                else -> ChatResponse(
                    text = "⏳ **Gemini Nano Downloading...**\n\n" +
                            "Model is downloading in the background (~1.5 GB, happens once).\n" +
                            "Try again in a few minutes, or switch to Gemini Pro in ⚙️ Settings!",
                    toolCalls = emptyList()
                )
            }
        }

        // If Nano succeeded, return result
        nanoResult.getOrNull()?.let { return it }

        // Nano failed or unavailable — check if it's a known device limitation
        val exception = nanoResult.exceptionOrNull()
        val isDeviceUnsupported = exception == null || // null means status was UNAVAILABLE
            exception.message?.contains("606") == true ||
            exception.message?.contains("FEATURE_NOT_FOUND", ignoreCase = true) == true ||
            exception.message?.contains("NOT_AVAILABLE", ignoreCase = true) == true ||
            exception.message?.contains("PREPARATION_ERROR", ignoreCase = true) == true

        // Silent auto-fallback chain: Gemini Pro → Custom Endpoint → friendly message
        if (isDeviceUnsupported) {
            if (!config.apiKey.isNullOrEmpty()) {
                return chatWithGeminiPro(messages, tools)
            }
            if (!config.endpoint.isNullOrEmpty()) {
                return chatWithCustomEndpoint(messages, tools)
            }
            return ChatResponse(
                text = "📱 **Gemini Nano Not Available on This Device**\n\n" +
                        "On-device AI requires a Pixel 8+ or Samsung Galaxy S24+ with Android 14+.\n\n" +
                        "**Get started in 2 mins:**\n" +
                        "1. Tap ⚙️ Settings\n" +
                        "2. Choose **Gemini Pro** (free key at https://aistudio.google.com/app/apikey)\n" +
                        "   — or —\n" +
                        "   Choose **Custom Endpoint** (Ollama, OpenAI, etc.)",
                toolCalls = emptyList()
            )
        }

        // Unknown error
        return ChatResponse(
            text = "❌ **Gemini Nano Error**\n\nError: ${exception?.message}\n\nSwitch to Gemini Pro or Custom Endpoint in ⚙️ Settings.",
            toolCalls = emptyList()
        )
    }

    private suspend fun chatWithGeminiPro(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse {
        return try {
            val apiKey = config.apiKey

            if (apiKey.isNullOrEmpty()) {
                return ChatResponse(
                    text = "⚙️ **API Key Required**\n\n" +
                            "Gemini Pro needs an API key. Get one free at:\n" +
                            "https://aistudio.google.com/app/apikey\n\n" +
                            "Then tap ⚙️ Settings → Gemini Pro → paste your key.\n\n" +
                            "**Alternative:** Use a Custom Endpoint (Ollama on your PC).",
                    toolCalls = emptyList()
                )
            }

            val model = GenerativeModel(
                modelName = config.model ?: "gemini-1.5-flash",
                apiKey = apiKey
            )

            val prompt = buildPrompt(messages, tools)
            val response = model.generateContent(prompt)

            parseResponse(response.text ?: "")
        } catch (e: Exception) {
            ChatResponse(
                text = "❌ Gemini Pro error: ${e.message}\n\n" +
                        "Check your API key in Settings or try a different provider.",
                toolCalls = emptyList()
            )
        }
    }

    /**
     * OpenAI-compatible chat completion endpoint.
     * Works with Ollama, LM Studio, OpenAI, Anthropic (via proxy), etc.
     */
    private suspend fun chatWithCustomEndpoint(
        messages: List<Message>,
        tools: List<ToolDefinition>
    ): ChatResponse {
        val endpoint = config.endpoint
        if (endpoint.isNullOrEmpty()) {
            return ChatResponse(
                text = "⚙️ **Endpoint URL Required**\n\n" +
                        "Set a Custom Endpoint URL in Settings.\n\n" +
                        "Examples:\n" +
                        "• Ollama: http://192.168.1.100:11434/v1/chat/completions\n" +
                        "• OpenAI: https://api.openai.com/v1/chat/completions\n" +
                        "• LM Studio: http://localhost:1234/v1/chat/completions",
                toolCalls = emptyList()
            )
        }

        return try {
            // Build system prompt with tool definitions
            val systemPrompt = buildSystemPrompt(tools)

            // Build messages array for OpenAI-compatible API
            val messagesArray = JSONArray()

            // System message first
            val systemMsg = JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            messagesArray.put(systemMsg)

            // Conversation history
            messages.forEach { msg ->
                val role = if (msg.isUser) "user" else "assistant"
                val msgObj = JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                }
                messagesArray.put(msgObj)
            }

            // Build request body
            val requestBody = JSONObject().apply {
                put("model", config.model ?: "llama3.2")
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_tokens", 2048)
                put("stream", false)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("Content-Type", "application/json")

            // Add auth header if API key provided
            if (!config.apiKey.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                return ChatResponse(
                    text = "❌ HTTP ${response.code} from endpoint.\n\nDetails: $errorBody",
                    toolCalls = emptyList()
                )
            }

            val responseBody = response.body?.string() ?: ""
            parseOpenAIResponse(responseBody)
        } catch (e: Exception) {
            ChatResponse(
                text = "❌ Custom endpoint error: ${e.message}\n\n" +
                        "Check the endpoint URL and network connectivity in Settings.",
                toolCalls = emptyList()
            )
        }
    }

    private fun buildSystemPrompt(tools: List<ToolDefinition>): String {
        val sb = StringBuilder()
        sb.appendLine("You are an AI assistant with access to Android device tools.")
        sb.appendLine()

        if (tools.isNotEmpty()) {
            sb.appendLine("## Available Tools")
            sb.appendLine()
            tools.forEach { tool ->
                sb.appendLine("### ${tool.name}")
                sb.appendLine("Description: ${tool.description}")
                if (tool.parameters.isNotEmpty()) {
                    sb.appendLine("Parameters:")
                    tool.parameters.forEach { (key, type) ->
                        sb.appendLine("  - $key ($type)")
                    }
                }
                sb.appendLine()
            }

            sb.appendLine("## Tool Usage")
            sb.appendLine()
            sb.appendLine("To use a tool, include a JSON block in your response:")
            sb.appendLine("""```json""")
            sb.appendLine("""{"tool": "tool_name", "params": {"param1": "value1", "param2": "value2"}}""")
            sb.appendLine("""```""")
            sb.appendLine()
            sb.appendLine("You can call multiple tools sequentially. After each tool call, you will receive the result.")
        }

        return sb.toString()
    }

    private fun buildPrompt(messages: List<Message>, tools: List<ToolDefinition>): String {
        val sb = StringBuilder()

        // System prompt with tool definitions
        sb.appendLine(buildSystemPrompt(tools))

        // Conversation history
        messages.forEach { msg ->
            val role = if (msg.isUser) "User" else "Assistant"
            sb.appendLine("$role: ${msg.content}")
        }

        return sb.toString()
    }

    /**
     * Parse an OpenAI-compatible chat completion response.
     */
    private fun parseOpenAIResponse(responseBody: String): ChatResponse {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                return ChatResponse(text = "Empty response from endpoint.", toolCalls = emptyList())
            }

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.optString("content", "")

            // Check for native tool_calls (OpenAI function calling format)
            val nativeToolCalls = mutableListOf<ToolCall>()
            if (message.has("tool_calls")) {
                val toolCallsArray = message.getJSONArray("tool_calls")
                for (i in 0 until toolCallsArray.length()) {
                    val tc = toolCallsArray.getJSONObject(i)
                    val functionObj = tc.getJSONObject("function")
                    val toolName = functionObj.getString("name")
                    val argsStr = functionObj.getString("arguments")
                    val params = mutableMapOf<String, Any>()
                    try {
                        val argsJson = JSONObject(argsStr)
                        argsJson.keys().forEach { key ->
                            params[key] = argsJson.get(key)
                        }
                    } catch (_: Exception) { /* ignore parse errors */ }
                    nativeToolCalls.add(ToolCall(toolName, params))
                }
            }

            // Also parse inline JSON tool calls from content text
            val inlineToolCalls = parseInlineToolCalls(content)

            val allToolCalls = nativeToolCalls + inlineToolCalls
            val cleanText = if (allToolCalls.isNotEmpty()) {
                removeJsonBlocks(content).trim()
            } else {
                content
            }

            ChatResponse(
                text = cleanText.ifEmpty { if (allToolCalls.isNotEmpty()) "Executing tools…" else "" },
                toolCalls = allToolCalls
            )
        } catch (e: Exception) {
            // If JSON parse fails, treat raw body as text
            ChatResponse(text = responseBody, toolCalls = emptyList())
        }
    }

    private fun parseResponse(text: String): ChatResponse {
        val toolCalls = parseInlineToolCalls(text)
        val cleanText = if (toolCalls.isNotEmpty()) removeJsonBlocks(text).trim() else text
        return ChatResponse(
            text = cleanText.ifEmpty { if (toolCalls.isNotEmpty()) "Executing tools…" else "" },
            toolCalls = toolCalls
        )
    }

    /** Extract tool calls embedded as JSON blocks in text responses. */
    private fun parseInlineToolCalls(text: String): List<ToolCall> {
        val toolCalls = mutableListOf<ToolCall>()

        // Match JSON objects that contain a "tool" key (possibly inside a code block)
        val jsonPattern = Regex("""(?:```json\s*)?(\{[^`]*?"tool"\s*:[^`]*?\})(?:\s*```)?""", RegexOption.DOT_MATCHES_ALL)
        jsonPattern.findAll(text).forEach { match ->
            try {
                val json = JSONObject(match.groupValues[1])
                val toolName = json.optString("tool")
                if (toolName.isNotEmpty()) {
                    val params = mutableMapOf<String, Any>()
                    if (json.has("params")) {
                        val paramsJson = json.getJSONObject("params")
                        paramsJson.keys().forEach { key ->
                            params[key] = paramsJson.get(key)
                        }
                    }
                    toolCalls.add(ToolCall(toolName, params))
                }
            } catch (_: Exception) { /* skip invalid JSON */ }
        }

        return toolCalls
    }

    private fun removeJsonBlocks(text: String): String {
        // Remove ```json ... ``` blocks
        var result = text.replace(Regex("""```json\s*\{[^`]*?\}\s*```""", RegexOption.DOT_MATCHES_ALL), "")
        // Remove bare {"tool": ...} blocks
        result = result.replace(Regex("""\{[^{}]*?"tool"\s*:[^{}]*?\}"""), "")
        return result
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
