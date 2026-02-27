package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import ai.openclaw.androidagent.services.AgentAccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class WaitTool : Tool {
    override val name = "wait"
    override val description = "Wait for a duration or until an element appears"
    override val parameters = mapOf(
        "seconds" to ToolParameter("number", "Seconds to wait (for simple delay)", required = false),
        "forText" to ToolParameter("string", "Wait until text appears (max 30s)", required = false),
        "timeout" to ToolParameter("number", "Timeout in seconds (default: 30)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val seconds = params["seconds"] as? Number
        val forText = params["forText"] as? String
        val timeout = (params["timeout"] as? Number)?.toLong() ?: 30L
        
        return when {
            seconds != null -> {
                // Simple delay
                val ms = (seconds.toDouble() * 1000).toLong()
                if (ms < 0 || ms > 60000) {
                    return ToolResult(false, error = "Wait duration must be between 0 and 60 seconds")
                }
                delay(ms)
                ToolResult(true, data = "Waited ${seconds}s")
            }
            
            forText != null -> {
                // Wait for element with text
                val service = AgentAccessibilityService.instance
                    ?: return ToolResult(false, error = "Accessibility service not active")
                
                val found = withTimeoutOrNull(timeout * 1000) {
                    waitForTextAppear(service, forText)
                }
                
                if (found == true) {
                    ToolResult(true, data = "Element with text '$forText' appeared")
                } else {
                    ToolResult(false, error = "Timeout waiting for text '$forText' (${timeout}s)")
                }
            }
            
            else -> ToolResult(false, error = "Must provide either 'seconds' or 'forText' parameter")
        }
    }
    
    private suspend fun waitForTextAppear(
        service: AgentAccessibilityService,
        searchText: String
    ): Boolean {
        while (true) {
            val rootNode = service.rootInActiveWindow
            if (rootNode != null) {
                val found = searchForText(rootNode, searchText)
                rootNode.recycle()
                if (found) return true
            }
            delay(500) // Check every 500ms
        }
    }
    
    private fun searchForText(node: AccessibilityNodeInfo?, searchText: String): Boolean {
        if (node == null) return false
        
        val nodeText = node.text?.toString()
        if (nodeText?.contains(searchText, ignoreCase = true) == true) {
            return true
        }
        
        val nodeDesc = node.contentDescription?.toString()
        if (nodeDesc?.contains(searchText, ignoreCase = true) == true) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            if (searchForText(node.getChild(i), searchText)) {
                return true
            }
        }
        
        return false
    }
}
