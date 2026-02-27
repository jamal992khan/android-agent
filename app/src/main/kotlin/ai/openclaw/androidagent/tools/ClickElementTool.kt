package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ClickElementTool : Tool {
    override val name = "click_element"
    override val description = "Find UI element by text and click it automatically"
    override val parameters = mapOf(
        "text" to ToolParameter("string", "Text or description of element to click", required = true),
        "index" to ToolParameter("number", "Index if multiple matches (default: 0)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not active")
        
        val searchText = params["text"] as? String
            ?: return ToolResult(false, error = "Missing 'text' parameter")
        
        val index = (params["index"] as? Number)?.toInt() ?: 0
        
        val rootNode = service.rootInActiveWindow
            ?: return ToolResult(false, error = "Cannot access screen content")
        
        val matches = mutableListOf<AccessibilityNodeInfo>()
        findClickableNodesByText(rootNode, searchText, matches)
        
        if (matches.isEmpty()) {
            rootNode.recycle()
            return ToolResult(false, error = "No clickable element found with text '$searchText'")
        }
        
        if (index >= matches.size) {
            rootNode.recycle()
            return ToolResult(
                false,
                error = "Index $index out of range. Found ${matches.size} matching element(s)."
            )
        }
        
        val targetNode = matches[index]
        val bounds = Rect()
        targetNode.getBoundsInScreen(bounds)
        
        val centerX = (bounds.left + bounds.right) / 2f
        val centerY = (bounds.top + bounds.bottom) / 2f
        
        // Try using performAction first (more reliable)
        val actionSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        
        rootNode.recycle()
        matches.forEach { it.recycle() }
        
        return if (actionSuccess) {
            ToolResult(true, data = "Clicked element: $searchText")
        } else {
            // Fallback to gesture
            performTapGesture(service, centerX, centerY, searchText)
        }
    }
    
    private suspend fun performTapGesture(
        service: AgentAccessibilityService,
        x: Float,
        y: Float,
        elementText: String
    ): ToolResult {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return suspendCancellableCoroutine { continuation ->
            val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    continuation.resume(ToolResult(true, data = "Clicked element: $elementText (via gesture)"))
                }
                
                override fun onCancelled(gestureDescription: GestureDescription) {
                    continuation.resume(ToolResult(false, error = "Click gesture was cancelled"))
                }
            }
            
            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                continuation.resume(ToolResult(false, error = "Failed to dispatch click gesture"))
            }
        }
    }
    
    private fun findClickableNodesByText(
        node: AccessibilityNodeInfo?,
        searchText: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        
        val nodeText = node.text?.toString()
        val nodeDesc = node.contentDescription?.toString()
        
        val matches = nodeText?.contains(searchText, ignoreCase = true) == true ||
                      nodeDesc?.contains(searchText, ignoreCase = true) == true
        
        if (matches && (node.isClickable || node.isFocusable)) {
            results.add(node)
        }
        
        for (i in 0 until node.childCount) {
            findClickableNodesByText(node.getChild(i), searchText, results)
        }
    }
}
