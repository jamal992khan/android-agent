package ai.openclaw.androidagent.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.openclaw.androidagent.core.AgentCore
import ai.openclaw.androidagent.core.LLMManager
import ai.openclaw.androidagent.core.ModelDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val agentCore = remember { AgentCore.getInstance(context) }
    val downloadManager = remember {
        ModelDownloadManager(context).also { it.initializeStates() }
    }
    val llamaEngine = remember { agentCore.getLLMManager().getLlamaEngine() }

    // Download states — polled every second while screen is visible
    val downloadStates by downloadManager.downloadStates.collectAsState()

    // Poll download progress
    LaunchedEffect(Unit) {
        while (isActive) {
            downloadManager.pollProgress()
            delay(1000L)
        }
    }

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
            if (selectedProvider == LLMManager.Provider.LOCAL) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🚀 Default: 100% Offline Local AI",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "\nLocal GGUF models run entirely on your OnePlus 13!\n\n" +
                            "Download a model below to get started.\n" +
                            "Once downloaded, the app works with NO internet required.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (apiKey.isEmpty() && selectedProvider == LLMManager.Provider.GEMINI_PRO) {
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
                        title = "Local GGUF Model ⭐ (100% Offline)",
                        description = "Best for OnePlus 13! Runs Llama/Phi entirely on-device. Download model below.",
                        selected = selectedProvider == LLMManager.Provider.LOCAL,
                        onClick = { selectedProvider = LLMManager.Provider.LOCAL }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    ProviderOption(
                        title = "Gemini Nano (On-Device)",
                        description = "On-device via AICore. Works on Pixel 8+, Galaxy S24+.",
                        selected = selectedProvider == LLMManager.Provider.GEMINI_NANO,
                        onClick = { selectedProvider = LLMManager.Provider.GEMINI_NANO }
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    ProviderOption(
                        title = "Gemini Pro (Cloud)",
                        description = "More capable, requires internet and API key. Good fallback.",
                        selected = selectedProvider == LLMManager.Provider.GEMINI_PRO,
                        onClick = { selectedProvider = LLMManager.Provider.GEMINI_PRO }
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
                LLMManager.Provider.LOCAL -> {
                    // Local Model section
                    LocalModelSection(
                        downloadManager = downloadManager,
                        llamaEngine = llamaEngine,
                        downloadStates = downloadStates
                    )
                }

                LLMManager.Provider.GEMINI_NANO -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "✅ Gemini Nano Ready!",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "\n**Your OnePlus 13 supports on-device AI!**\n\n" +
                                "If you see 'NOT_AVAILABLE' error:\n" +
                                "1. Play Store → 'Android AICore'\n" +
                                "2. Join Beta → Wait for update (~5 min)\n" +
                                "3. Restart app\n\n" +
                                "Benefits:\n" +
                                "• ⚡ Lightning fast (no network)\n" +
                                "• 🔒 100% private (never leaves device)\n" +
                                "• 📴 Works offline\n" +
                                "• 🆓 Completely free\n\n" +
                                "Tap Save and start chatting!",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
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
fun LocalModelSection(
    downloadManager: ModelDownloadManager,
    llamaEngine: ai.openclaw.androidagent.core.LlamaEngine,
    downloadStates: Map<String, ModelDownloadManager.DownloadState>
) {
    val loadedModel = llamaEngine.getLoadedModelName()
    val storageUsed = remember(downloadStates) { downloadManager.totalStorageUsedDisplay() }

    // Header info card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🤖 Local GGUF Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (loadedModel != null) {
                Text(
                    "✅ Active: $loadedModel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "No model loaded. Download one below.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (storageUsed != "0 MB") {
                Text(
                    "💾 Storage used: $storageUsed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Model cards
    ModelDownloadManager.AVAILABLE_MODELS.forEach { model ->
        val state = downloadStates[model.id]
            ?: ModelDownloadManager.DownloadState(model.id, ModelDownloadManager.DownloadState.Status.IDLE)
        val isDownloaded = downloadManager.isModelDownloaded(model)

        ModelDownloadCard(
            model = model,
            state = state,
            isDownloaded = isDownloaded,
            isActive = loadedModel == model.fileName,
            onDownload = { downloadManager.startDownload(model) },
            onCancel = { downloadManager.cancelDownload(model.id) },
            onDelete = { downloadManager.deleteModel(model) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ModelDownloadCard(
    model: ModelDownloadManager.ModelInfo,
    state: ModelDownloadManager.DownloadState,
    isDownloaded: Boolean,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Size: ${model.displaySize} · RAM: ${model.ramRequiredMb / 1024}GB+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                state.status == ModelDownloadManager.DownloadState.Status.DOWNLOADING ||
                state.status == ModelDownloadManager.DownloadState.Status.QUEUED -> {
                    // Show progress bar
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dlMb = state.downloadedBytes / 1024 / 1024
                        val totalMb = if (state.totalBytes > 0) state.totalBytes / 1024 / 1024 else model.sizeBytes / 1024 / 1024
                        Text(
                            "${state.progressPercent}% (${dlMb}MB / ${totalMb}MB)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }
                }

                isDownloaded || state.status == ModelDownloadManager.DownloadState.Status.COMPLETED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isActive) {
                            OutlinedButton(
                                onClick = { /* model will auto-load on next chat */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Use This Model")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("✓ Active")
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }

                state.status == ModelDownloadManager.DownloadState.Status.FAILED -> {
                    Text(
                        "❌ ${state.errorMessage ?: "Download failed"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Download")
                    }
                }

                else -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download (${model.displaySize})")
                    }
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
    // Default to LOCAL provider for new installs (100% offline on OnePlus 13!)
    val providerName = prefs.getString("provider", LLMManager.Provider.LOCAL.name)

    val provider = try {
        LLMManager.Provider.valueOf(providerName ?: LLMManager.Provider.LOCAL.name)
    } catch (e: IllegalArgumentException) {
        LLMManager.Provider.LOCAL
    }

    return LLMManager.LLMConfig(
        provider = provider,
        apiKey = prefs.getString("api_key", null),
        endpoint = prefs.getString("endpoint", null),
        model = prefs.getString("model", null)
    )
}
