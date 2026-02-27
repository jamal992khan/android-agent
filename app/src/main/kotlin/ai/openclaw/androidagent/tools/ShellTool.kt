package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShellTool : Tool {
    override val name = "shell"
    override val description = "Execute shell command (requires root if useRoot=true)"
    override val parameters = mapOf(
        "command" to ToolParameter("string", "Shell command to execute", required = true),
        "useRoot" to ToolParameter("boolean", "Execute as root", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val command = params["command"] as? String
            ?: return@withContext ToolResult(false, error = "Invalid command parameter")
        
        val useRoot = params["useRoot"] as? Boolean ?: false
        
        try {
            if (useRoot) {
                if (!Shell.SU.available()) {
                    return@withContext ToolResult(false, error = "Root access not available")
                }
                
                val output = Shell.SU.run(command)
                ToolResult(true, data = output?.joinToString("\n") ?: "")
            } else {
                val output = Shell.SH.run(command)
                ToolResult(true, data = output?.joinToString("\n") ?: "")
            }
        } catch (e: Exception) {
            ToolResult(false, error = "Command failed: ${e.message}")
        }
    }
}
