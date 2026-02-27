package ai.openclaw.androidagent.core

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * LlamaEngine: 100% offline on-device LLM inference via MediaPipe LLM Inference API.
 *
 * Designed for OnePlus 13 (Snapdragon 8 Elite, 16GB RAM).
 * Supports GGUF models — streaming via setResultListener on options builder.
 *
 * MediaPipe 0.10.14 API notes:
 *  - Streaming is set via setResultListener on LlmInferenceOptions.Builder
 *  - generateResponseAsync(prompt) is void — result comes via listener
 *  - generateResponse(prompt) is synchronous (blocking)
 *  - No setPreferredBackend in 0.10.14 — GPU auto-selected when available
 */
class LlamaEngine(private val context: Context) {

    companion object {
        private const val TAG = "LlamaEngine"
        const val MODELS_DIR = "models"

        val MODEL_PRIORITY = listOf(
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        )

        private const val MAX_TOKENS = 2048
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.8f
    }

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var loadedModelName: String? = null

    fun getModelPath(): String = File(context.filesDir, MODELS_DIR).absolutePath
    fun getModelsDir(): File = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    fun listDownloadedModels(): List<File> =
        getModelsDir().listFiles { f -> f.name.endsWith(".gguf") }?.toList() ?: emptyList()
    fun getLoadedModelName(): String? = loadedModelName
    suspend fun isModelLoaded(): Boolean = withContext(Dispatchers.IO) { llmInference != null }

    suspend fun loadBestModel(): Boolean = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext true

        val modelsDir = getModelsDir()
        val modelFile = MODEL_PRIORITY
            .map { File(modelsDir, it) }
            .firstOrNull { it.exists() && it.length() > 0L }
            ?: modelsDir.listFiles { f -> f.name.endsWith(".gguf") && f.length() > 0L }
                ?.maxByOrNull { it.length() }

        if (modelFile == null) {
            Log.i(TAG, "No model files found in ${modelsDir.absolutePath}")
            return@withContext false
        }
        return@withContext loadModel(modelFile)
    }

    suspend fun loadModel(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) return@withContext false
        closeModel()

        return@withContext try {
            // Build options WITHOUT setResultListener — for synchronous use
            // Streaming inference builds its own options with listener attached
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelName = modelFile.name
            Log.i(TAG, "Model loaded: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            loadedModelName = null
            false
        }
    }

    /**
     * Streaming generation — uses a separate LlmInference instance with setResultListener.
     * Each call creates/destroys its own inference session for streaming.
     */
    suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        // Check a model file exists first
        val modelFile = MODEL_PRIORITY
            .map { File(getModelsDir(), it) }
            .firstOrNull { it.exists() && it.length() > 0L }
            ?: getModelsDir().listFiles { f -> f.name.endsWith(".gguf") && f.length() > 0L }
                ?.maxByOrNull { it.length() }
            ?: return@withContext buildNoModelMessage()

        return@withContext suspendCancellableCoroutine { continuation ->
            val responseBuilder = StringBuilder()
            var hasResumed = false
            var streamingInference: LlmInference? = null

            try {
                // In 0.10.14, streaming is configured via setResultListener on builder
                val streamOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .setRandomSeed(42)
                    .setResultListener { partialResult, done ->
                        if (partialResult != null) {
                            responseBuilder.append(partialResult)
                            onToken(partialResult)
                        }
                        if (done && !hasResumed) {
                            hasResumed = true
                            streamingInference?.close()
                            streamingInference = null
                            continuation.resume(responseBuilder.toString().trim())
                        }
                    }
                    .setErrorListener { e ->
                        if (!hasResumed) {
                            hasResumed = true
                            streamingInference?.close()
                            streamingInference = null
                            continuation.resumeWithException(e)
                        }
                    }
                    .build()

                streamingInference = LlmInference.createFromOptions(context, streamOptions)
                streamingInference!!.generateResponseAsync(prompt)

            } catch (e: Exception) {
                if (!hasResumed) {
                    hasResumed = true
                    streamingInference?.close()
                    Log.e(TAG, "Streaming generation failed: ${e.message}")
                    continuation.resumeWithException(e)
                }
            }

            continuation.invokeOnCancellation {
                streamingInference?.close()
                Log.d(TAG, "Generation cancelled")
            }
        }
    }

    /**
     * Synchronous generation — uses the pre-loaded inference instance.
     */
    suspend fun generateSync(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = llmInference ?: run {
            // Try loading if not loaded
            if (!loadBestModel()) return@withContext buildNoModelMessage()
            llmInference ?: return@withContext buildNoModelMessage()
        }
        return@withContext try {
            inference.generateResponse(prompt)?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Sync generation failed: ${e.message}")
            "❌ Generation error: ${e.message}"
        }
    }

    fun closeModel() {
        llmInference?.close()
        llmInference = null
        loadedModelName = null
    }

    private fun buildNoModelMessage(): String = """
        📱 **No Local Model Loaded**
        
        Download a GGUF model to use 100% offline AI:
        
        1. Go to ⚙️ Settings → Local Model
        2. Tap **Download** next to any model
        3. Wait (~1-2GB download)
        4. Return here and chat offline!
        
        **Recommended for your OnePlus 13:**
        • Llama 3.2 3B (~2GB) — best quality
        • Phi-3.5 Mini (~2.2GB) — fastest  
        • Llama 3.2 1B (~1GB) — smallest
    """.trimIndent()
}
