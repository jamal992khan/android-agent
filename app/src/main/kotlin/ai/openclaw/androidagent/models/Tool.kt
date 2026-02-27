package ai.openclaw.androidagent.models

import org.json.JSONObject

/**
 * Represents an executable tool that the agent can use
 */
interface Tool {
    val name: String
    val description: String
    val parameters: Map<String, ToolParameter>
    
    suspend fun execute(params: Map<String, Any>): ToolResult
}

data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean = false
)

data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null
)

/**
 * Tool function call from LLM
 */
data class ToolCall(
    val name: String,
    val parameters: JSONObject
)
