package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import ai.openclaw.androidagent.services.AgentAccessibilityService

class TypeTextTool : Tool {
    override val name = "type_text"
    override val description = "Type text into the currently focused input field"
    override val parameters = mapOf(
        "text" to ToolParameter("string", "Text to type", required = true)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not enabled")
        
        val text = params["text"] as? String
            ?: return ToolResult(false, error = "Invalid text parameter")
        
        val rootNode = service.rootInActiveWindow
            ?: return ToolResult(false, error = "No active window")
        
        val focusedNode = findFocusedEditableNode(rootNode)
            ?: return ToolResult(false, error = "No focused input field found")
        
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        
        return if (success) {
            ToolResult(true, data = "Typed: $text")
        } else {
            ToolResult(false, error = "Failed to type text")
        }
    }
    
    private fun findFocusedEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findFocusedEditableNode(child)?.let { return it }
            }
        }
        
        return null
    }
}
