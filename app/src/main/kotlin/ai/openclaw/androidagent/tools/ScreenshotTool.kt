package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService

class ScreenshotTool : Tool {
    override val name = "screenshot"
    override val description = "Get current screen hierarchy as JSON"
    override val parameters = emptyMap<String, ToolParameter>()
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not enabled")
        
        val hierarchy = service.getScreenHierarchy()
        
        return if (hierarchy.has("error")) {
            ToolResult(false, error = hierarchy.getString("error"))
        } else {
            ToolResult(true, data = hierarchy.toString(2))
        }
    }
}
