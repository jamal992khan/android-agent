package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LongPressTool : Tool {
    override val name = "long_press"
    override val description = "Long press at coordinates to trigger context menu or selection"
    override val parameters = mapOf(
        "x" to ToolParameter("number", "X coordinate", required = true),
        "y" to ToolParameter("number", "Y coordinate", required = true),
        "duration" to ToolParameter("number", "Duration in milliseconds (default: 1000)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not active")
        
        val x = (params["x"] as? Number)?.toFloat()
            ?: return ToolResult(false, error = "Invalid x coordinate")
        
        val y = (params["y"] as? Number)?.toFloat()
            ?: return ToolResult(false, error = "Invalid y coordinate")
        
        val duration = (params["duration"] as? Number)?.toLong() ?: 1000L
        
        if (duration < 100 || duration > 10000) {
            return ToolResult(false, error = "Duration must be between 100 and 10000 milliseconds")
        }
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return suspendCancellableCoroutine { continuation ->
            val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    continuation.resume(ToolResult(true, data = "Long pressed at ($x, $y) for ${duration}ms"))
                }
                
                override fun onCancelled(gestureDescription: GestureDescription) {
                    continuation.resume(ToolResult(false, error = "Long press was cancelled"))
                }
            }
            
            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                continuation.resume(ToolResult(false, error = "Failed to dispatch long press gesture"))
            }
        }
    }
}
