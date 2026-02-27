package ai.openclaw.androidagent.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.openclaw.androidagent.core.AgentCore
import ai.openclaw.androidagent.core.LLMManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val agentCore = remember { AgentCore.getInstance(context) }
    
    // Load current config
    val currentConfig = remember { loadConfig(context) }
    
    var selectedProvider by remember { mutableStateOf(currentConfig.provider) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey ?: "") }
    var endpoint by remember { mutableStateOf(currentConfig.endpoint ?: "") }
    var modelName by remember { mutableStateOf(currentConfig.model ?: "gemini-1.5-flash") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "LLM Provider",
                style = MaterialTheme.typography.titleLarge
            )
            
            // First-run helper
            if (apiKey.isEmpty() && selectedProvider == LLMManager.Provider.GEMINI_PRO) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "👋 Quick Setup",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "\n1. Get a free API key: https://makersuite.google.com/app/apikey\n" +
                            "2. Paste it below\n" +
                            "3. Tap Save\n\n" +
                            "The agent will then be able to respond intelligently!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Provider selection
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProviderOption(
                        title = "Gemini Pro (Cloud) ⭐",
                        description = "Recommended. Fast, capable, works on all devices. Free tier available.",
                        selected = selectedProvider == LLMManager.Provider.GEMINI_PRO,
                        onClick = { selectedProvider = LLMManager.Provider.GEMINI_PRO }
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    ProviderOption(
                        title = "Gemini Nano (On-Device)",
                        description = "Free, private, offline. Only works on Pixel 8+ / Samsung S24+.",
                        selected = selectedProvider == LLMManager.Provider.GEMINI_NANO,
                        onClick = { selectedProvider = LLMManager.Provider.GEMINI_NANO }
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    ProviderOption(
                        title = "Custom Endpoint",
                        description = "Use Ollama, OpenAI, or any compatible API.",
                        selected = selectedProvider == LLMManager.Provider.CUSTOM,
                        onClick = { selectedProvider = LLMManager.Provider.CUSTOM }
                    )
                }
            }
            
            // Configuration fields based on selected provider
            when (selectedProvider) {
                LLMManager.Provider.GEMINI_NANO -> {
                    Text(
                        text = "✅ No configuration needed!\n\n" +
                               "Gemini Nano runs entirely on your device. " +
                               "If you see errors, your device may not support it yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                LLMManager.Provider.GEMINI_PRO -> {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("Get from https://makersuite.google.com/app/apikey") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("gemini-1.5-flash") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                LLMManager.Provider.CUSTOM -> {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Endpoint URL") },
                        placeholder = { Text("http://192.168.1.100:11434/v1/chat/completions") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key (optional)") },
                        placeholder = { Text("Leave empty if not required") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Save button
            Button(
                onClick = {
                    val config = LLMManager.LLMConfig(
                        provider = selectedProvider,
                        apiKey = apiKey.ifEmpty { null },
                        endpoint = endpoint.ifEmpty { null },
                        model = modelName.ifEmpty { null }
                    )
                    agentCore.configureLLM(config)
                    
                    // Save to preferences
                    saveConfig(context, config)
                    
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Configuration")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Quick Setup Guides",
                style = MaterialTheme.typography.titleMedium
            )
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📱 Ollama (Desktop)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. Install Ollama on your PC\n" +
                        "2. Run: ollama serve\n" +
                        "3. Use: http://YOUR_PC_IP:11434/v1/chat/completions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

private fun saveConfig(context: Context, config: LLMManager.LLMConfig) {
    val prefs = context.getSharedPreferences("llm_config", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString("provider", config.provider.name)
        putString("api_key", config.apiKey)
        putString("endpoint", config.endpoint)
        putString("model", config.model)
        apply()
    }
}

fun loadConfig(context: Context): LLMManager.LLMConfig {
    val prefs = context.getSharedPreferences("llm_config", Context.MODE_PRIVATE)
    val providerName = prefs.getString("provider", LLMManager.Provider.GEMINI_PRO.name)
    
    return LLMManager.LLMConfig(
        provider = LLMManager.Provider.valueOf(providerName ?: LLMManager.Provider.GEMINI_PRO.name),
        apiKey = prefs.getString("api_key", null),
        endpoint = prefs.getString("endpoint", null),
        model = prefs.getString("model", null)
    )
}
