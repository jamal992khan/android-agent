package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class ScrollTool : Tool {
    override val name = "scroll"
    override val description = "Scroll the screen or a specific scrollable element"
    override val parameters = mapOf(
        "direction" to ToolParameter("string", "Direction: 'up', 'down', 'left', or 'right'", required = true),
        "elementText" to ToolParameter("string", "Text of element to scroll (optional, scrolls screen if omitted)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not active")
        
        val direction = (params["direction"] as? String)?.lowercase()
            ?: return ToolResult(false, error = "Invalid direction parameter")
        
        val elementText = params["elementText"] as? String
        
        val action = when (direction) {
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> return ToolResult(
                false,
                error = "Unknown direction '$direction'. Use: up, down, left, or right"
            )
        }
        
        val rootNode = service.rootInActiveWindow
            ?: return ToolResult(false, error = "Cannot access screen content")
        
        val targetNode = if (elementText != null) {
            findScrollableNodeByText(rootNode, elementText)
        } else {
            findFirstScrollableNode(rootNode)
        }
        
        val success = if (targetNode != null) {
            val result = targetNode.performAction(action)
            targetNode.recycle()
            result
        } else {
            false
        }
        
        rootNode.recycle()
        
        return if (success) {
            ToolResult(true, data = "Scrolled $direction")
        } else {
            ToolResult(
                false,
                error = if (elementText != null) {
                    "No scrollable element found with text '$elementText'"
                } else {
                    "No scrollable element found on screen"
                }
            )
        }
    }
    
    private fun findScrollableNodeByText(
        node: AccessibilityNodeInfo?,
        searchText: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        
        val nodeText = node.text?.toString()
        if (nodeText?.contains(searchText, ignoreCase = true) == true && node.isScrollable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val result = findScrollableNodeByText(node.getChild(i), searchText)
            if (result != null) return result
        }
        
        return null
    }
    
    private fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        
        if (node.isScrollable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val result = findFirstScrollableNode(node.getChild(i))
            if (result != null) return result
        }
        
        return null
    }
}
