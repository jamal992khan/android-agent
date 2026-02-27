package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClipboardTool(private val context: Context) : Tool {
    override val name = "clipboard"
    override val description = "Get or set clipboard content"
    override val parameters = mapOf(
        "action" to ToolParameter("string", "Action: 'get' or 'set'", required = true),
        "text" to ToolParameter("string", "Text to copy (only for 'set' action)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.Main) {
        val action = (params["action"] as? String)?.lowercase()
            ?: return@withContext ToolResult(false, error = "Invalid action parameter")
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        when (action) {
            "get", "read" -> {
                val clip = clipboard.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    ToolResult(true, data = "Clipboard is empty")
                } else {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    ToolResult(true, data = "Clipboard: $text")
                }
            }
            
            "set", "copy", "write" -> {
                val text = params["text"] as? String
                    ?: return@withContext ToolResult(false, error = "Missing 'text' parameter for set action")
                
                val clip = ClipData.newPlainText("agent_clipboard", text)
                clipboard.setPrimaryClip(clip)
                
                ToolResult(true, data = "Copied to clipboard: $text")
            }
            
            "clear" -> {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                ToolResult(true, data = "Clipboard cleared")
            }
            
            else -> ToolResult(false, error = "Unknown action '$action'. Use: get, set, or clear")
        }
    }
}
