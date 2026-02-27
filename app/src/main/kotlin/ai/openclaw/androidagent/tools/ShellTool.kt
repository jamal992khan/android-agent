package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

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
            val finalCommand = if (useRoot) {
                arrayOf("su", "-c", command)
            } else {
                arrayOf("sh", "-c", command)
            }
            
            val process = Runtime.getRuntime().exec(finalCommand)
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()
                .joinToString("\n")
            val error = BufferedReader(InputStreamReader(process.errorStream))
                .readText()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                ToolResult(true, data = output)
            } else {
                ToolResult(false, error = "Command failed (exit $exitCode): $error")
            }
        } catch (e: Exception) {
            ToolResult(false, error = "Command failed: ${e.message}")
        }
    }
}
