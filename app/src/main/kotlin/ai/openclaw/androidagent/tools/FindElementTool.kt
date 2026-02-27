package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class FindElementTool : Tool {
    override val name = "find_element"
    override val description = "Find UI element by text, description, or resource ID. Returns coordinates and info."
    override val parameters = mapOf(
        "text" to ToolParameter("string", "Text to search for (exact or partial match)", required = false),
        "description" to ToolParameter("string", "Content description to search for", required = false),
        "resourceId" to ToolParameter("string", "Resource ID to search for (e.g., 'com.android.calculator2:id/digit_5')", required = false),
        "partialMatch" to ToolParameter("boolean", "Allow partial text matching (default: true)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(false, error = "Accessibility service not active")
        
        val text = params["text"] as? String
        val description = params["description"] as? String
        val resourceId = params["resourceId"] as? String
        val partialMatch = params["partialMatch"] as? Boolean ?: true
        
        if (text == null && description == null && resourceId == null) {
            return ToolResult(false, error = "Must provide at least one search criterion: text, description, or resourceId")
        }
        
        val rootNode = service.rootInActiveWindow
            ?: return ToolResult(false, error = "Cannot access screen content")
        
        val matches = mutableListOf<ElementInfo>()
        findMatchingNodes(rootNode, text, description, resourceId, partialMatch, matches)
        
        rootNode.recycle()
        
        return if (matches.isEmpty()) {
            ToolResult(false, error = "No matching elements found")
        } else {
            val result = buildString {
                appendLine("Found ${matches.size} matching element(s):")
                matches.forEachIndexed { index, info ->
                    appendLine()
                    appendLine("Element ${index + 1}:")
                    appendLine("  Text: ${info.text ?: "none"}")
                    appendLine("  Description: ${info.description ?: "none"}")
                    appendLine("  Resource ID: ${info.resourceId ?: "none"}")
                    appendLine("  Bounds: (${info.centerX}, ${info.centerY})")
                    appendLine("  Clickable: ${info.clickable}")
                    appendLine("  Class: ${info.className}")
                }
            }
            ToolResult(true, data = result)
        }
    }
    
    private fun findMatchingNodes(
        node: AccessibilityNodeInfo?,
        searchText: String?,
        searchDescription: String?,
        searchResourceId: String?,
        partialMatch: Boolean,
        results: MutableList<ElementInfo>
    ) {
        if (node == null) return
        
        var matches = false
        
        if (searchText != null) {
            val nodeText = node.text?.toString()
            matches = if (partialMatch) {
                nodeText?.contains(searchText, ignoreCase = true) == true
            } else {
                nodeText?.equals(searchText, ignoreCase = true) == true
            }
        }
        
        if (!matches && searchDescription != null) {
            val nodeDesc = node.contentDescription?.toString()
            matches = if (partialMatch) {
                nodeDesc?.contains(searchDescription, ignoreCase = true) == true
            } else {
                nodeDesc?.equals(searchDescription, ignoreCase = true) == true
            }
        }
        
        if (!matches && searchResourceId != null) {
            val nodeResId = node.viewIdResourceName
            matches = if (partialMatch) {
                nodeResId?.contains(searchResourceId, ignoreCase = true) == true
            } else {
                nodeResId?.equals(searchResourceId, ignoreCase = true) == true
            }
        }
        
        if (matches) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            
            results.add(
                ElementInfo(
                    text = node.text?.toString(),
                    description = node.contentDescription?.toString(),
                    resourceId = node.viewIdResourceName,
                    centerX = (bounds.left + bounds.right) / 2,
                    centerY = (bounds.top + bounds.bottom) / 2,
                    clickable = node.isClickable,
                    className = node.className?.toString()
                )
            )
        }
        
        // Recursively search children
        for (i in 0 until node.childCount) {
            findMatchingNodes(
                node.getChild(i),
                searchText,
                searchDescription,
                searchResourceId,
                partialMatch,
                results
            )
        }
    }
    
    data class ElementInfo(
        val text: String?,
        val description: String?,
        val resourceId: String?,
        val centerX: Int,
        val centerY: Int,
        val clickable: Boolean,
        val className: String?
    )
}
