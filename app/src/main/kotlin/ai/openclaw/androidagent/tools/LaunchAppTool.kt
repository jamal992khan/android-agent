package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LaunchAppTool(private val context: Context) : Tool {
    override val name = "launch_app"
    override val description = "Launch an app by package name or search by app name"
    override val parameters = mapOf(
        "packageName" to ToolParameter("string", "Package name (e.g., 'com.android.chrome')", required = false),
        "appName" to ToolParameter("string", "App name to search for (e.g., 'Chrome')", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val packageName = params["packageName"] as? String
        val appName = params["appName"] as? String
        
        if (packageName == null && appName == null) {
            return@withContext ToolResult(
                false,
                error = "Must provide either packageName or appName"
            )
        }
        
        val pm = context.packageManager
        
        val targetPackage = when {
            packageName != null -> packageName
            appName != null -> {
                // Search installed apps
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val matches = apps.filter { app ->
                    val label = pm.getApplicationLabel(app).toString()
                    label.contains(appName, ignoreCase = true)
                }
                
                if (matches.isEmpty()) {
                    return@withContext ToolResult(
                        false,
                        error = "No app found matching '$appName'"
                    )
                } else if (matches.size > 1) {
                    val matchList = matches.joinToString("\n") { app ->
                        "- ${pm.getApplicationLabel(app)} (${app.packageName})"
                    }
                    return@withContext ToolResult(
                        false,
                        error = "Multiple apps found matching '$appName':\n$matchList\n\nUse packageName parameter for exact match."
                    )
                }
                
                matches.first().packageName
            }
            else -> return@withContext ToolResult(false, error = "Invalid parameters")
        }
        
        try {
            val intent = pm.getLaunchIntentForPackage(targetPackage)
                ?: return@withContext ToolResult(
                    false,
                    error = "App '$targetPackage' has no launch activity"
                )
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            val appLabel = pm.getApplicationLabel(
                pm.getApplicationInfo(targetPackage, 0)
            )
            
            ToolResult(true, data = "Launched $appLabel ($targetPackage)")
        } catch (e: Exception) {
            ToolResult(false, error = "Failed to launch app: ${e.message}")
        }
    }
}
