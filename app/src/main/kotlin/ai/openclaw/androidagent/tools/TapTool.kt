package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService

class TapTool : Tool {
    override val name = "tap"
    override val description = "Tap at specific screen coordinates"
    override val parameters = mapOf(
        "x" to ToolParameter("integer", "X coordinate", required = true),
        "y" to ToolParameter("integer", "Y coordinate", required = true),
        "duration" to ToolParameter("integer", "Tap duration in milliseconds", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not enabled")
        
        val x = (params["x"] as? Number)?.toInt()
            ?: return ToolResult(false, error = "Invalid x coordinate")
        val y = (params["y"] as? Number)?.toInt()
            ?: return ToolResult(false, error = "Invalid y coordinate")
        val duration = (params["duration"] as? Number)?.toLong() ?: 100L
        
        val success = service.performTap(x, y, duration)
        return if (success) {
            ToolResult(true, data = "Tapped at ($x, $y)")
        } else {
            ToolResult(false, error = "Failed to perform tap")
        }
    }
}
