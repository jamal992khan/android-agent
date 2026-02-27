package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.content.Context

class GetCurrentAppTool(private val context: Context) : Tool {
    override val name = "get_current_app"
    override val description = "Get information about the currently active app"
    override val parameters = emptyMap<String, ToolParameter>()
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not active")
        
        val rootNode = service.rootInActiveWindow
            ?: return ToolResult(false, error = "Cannot access active window")
        
        val packageName = rootNode.packageName?.toString()
        rootNode.recycle()
        
        if (packageName == null) {
            return ToolResult(false, error = "Could not determine current app")
        }
        
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            
            val result = buildString {
                appendLine("Current App:")
                appendLine("  Name: $appLabel")
                appendLine("  Package: $packageName")
            }
            
            return ToolResult(true, data = result)
        } catch (e: Exception) {
            return ToolResult(true, data = "Package: $packageName\n(App name unavailable)")
        }
    }
}
