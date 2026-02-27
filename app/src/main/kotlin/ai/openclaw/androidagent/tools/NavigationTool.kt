package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.accessibilityservice.AccessibilityService

class NavigationTool : Tool {
    override val name = "navigate"
    override val description = "Perform system navigation: back, home, or recent apps"
    override val parameters = mapOf(
        "action" to ToolParameter("string", "Navigation action: 'back', 'home', or 'recents'", required = true)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not active")
        
        val action = (params["action"] as? String)?.lowercase()
            ?: return ToolResult(false, error = "Invalid action parameter")
        
        val globalAction = when (action) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents", "recent", "overview" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return ToolResult(
                false,
                error = "Unknown action '$action'. Use: back, home, or recents"
            )
        }
        
        val success = service.performGlobalAction(globalAction)
        
        return if (success) {
            ToolResult(true, data = "Performed navigation: $action")
        } else {
            ToolResult(false, error = "Failed to perform navigation action")
        }
    }
}
