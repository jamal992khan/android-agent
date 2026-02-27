package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService

class SwipeTool : Tool {
    override val name = "swipe"
    override val description = "Swipe from one point to another"
    override val parameters = mapOf(
        "startX" to ToolParameter("integer", "Start X coordinate", required = true),
        "startY" to ToolParameter("integer", "Start Y coordinate", required = true),
        "endX" to ToolParameter("integer", "End X coordinate", required = true),
        "endY" to ToolParameter("integer", "End Y coordinate", required = true),
        "duration" to ToolParameter("integer", "Swipe duration in milliseconds", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not enabled")
        
        val startX = (params["startX"] as? Number)?.toInt()
            ?: return ToolResult(false, error = "Invalid startX")
        val startY = (params["startY"] as? Number)?.toInt()
            ?: return ToolResult(false, error = "Invalid startY")
        val endX = (params["endX"] as? Number)?.toInt()
            ?: return ToolResult(false, error = "Invalid endX")
        val endY = (params["endY"] as? Number)?.toInt()
            ?: return ToolResult(false, error = "Invalid endY")
        val duration = (params["duration"] as? Number)?.toLong() ?: 300L
        
        val success = service.performSwipe(startX, startY, endX, endY, duration)
        return if (success) {
            ToolResult(true, data = "Swiped from ($startX, $startY) to ($endX, $endY)")
        } else {
            ToolResult(false, error = "Failed to perform swipe")
        }
    }
}
