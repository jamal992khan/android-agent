package ai.openclaw.androidagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ai.openclaw.androidagent.core.AgentCore
import ai.openclaw.androidagent.ui.theme.AndroidAgentTheme
import ai.openclaw.androidagent.ui.screens.ChatScreen
import ai.openclaw.androidagent.ui.screens.SettingsScreen
import ai.openclaw.androidagent.ui.screens.loadConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load saved LLM configuration
        val savedConfig = loadConfig(this)
        val agentCore = AgentCore.getInstance(this)
        agentCore.configureLLM(savedConfig)
        
        setContent {
            AndroidAgentTheme {
                var showSettings by remember { mutableStateOf(false) }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(onBack = { showSettings = false })
                    } else {
                        ChatScreen(onSettingsClick = { showSettings = true })
                    }
                }
            }
        }
    }
}
