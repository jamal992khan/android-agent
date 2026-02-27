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
 * Supports GGUF models with GPU acceleration (Adreno GPU via MediaPipe's GPU delegate).
 *
 * Models are stored at: /data/data/ai.openclaw.androidagent/files/models/
 *
 * Supported models (download via ModelDownloadManager):
 *  - Llama-3.2-1B-Instruct-Q4_K_M.gguf  (~1GB)
 *  - Llama-3.2-3B-Instruct-Q4_K_M.gguf  (~2GB)
 *  - Phi-3.5-mini-instruct-Q4_K_M.gguf  (~2.2GB)
 */
class LlamaEngine(private val context: Context) {

    companion object {
        private const val TAG = "LlamaEngine"
        const val MODELS_DIR = "models"

        // Preferred model priority (first found wins)
        val MODEL_PRIORITY = listOf(
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        )

        // MediaPipe's max sequence length — conservative for RAM safety
        private const val MAX_TOKENS = 2048
    }

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var loadedModelName: String? = null

    /** Returns the path to the models directory. */
    fun getModelPath(): String {
        return File(context.filesDir, MODELS_DIR).absolutePath
    }

    /** Returns the models directory as a File, creating it if needed. */
    fun getModelsDir(): File {
        return File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }

    /** Returns all downloaded model files. */
    fun listDownloadedModels(): List<File> {
        return getModelsDir().listFiles { f -> f.name.endsWith(".gguf") }?.toList() ?: emptyList()
    }

    /** Returns the name of the currently loaded model, or null. */
    fun getLoadedModelName(): String? = loadedModelName

    /** Returns true if a model is currently loaded and ready. */
    suspend fun isModelLoaded(): Boolean = withContext(Dispatchers.IO) {
        llmInference != null
    }

    /**
     * Tries to load the best available model from internal storage.
     * Returns true if a model was loaded, false if none found.
     */
    suspend fun loadBestModel(): Boolean = withContext(Dispatchers.IO) {
        // Already loaded?
        if (llmInference != null) return@withContext true

        val modelsDir = getModelsDir()

        // Try preferred order first, then any .gguf file
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

    /**
     * Loads a specific model file.
     * Closes any previously loaded model first.
     */
    suspend fun loadModel(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            return@withContext false
        }

        Log.i(TAG, "Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)")

        // Close previous inference session
        closeModel()

        return@withContext try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                // Use GPU acceleration — MediaPipe auto-selects Adreno GPU delegate on Snapdragon
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelName = modelFile.name
            Log.i(TAG, "Model loaded successfully: ${modelFile.name}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "GPU backend failed, falling back to CPU: ${e.message}")
            // Fall back to CPU if GPU isn't available
            try {
                val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()

                llmInference = LlmInference.createFromOptions(context, cpuOptions)
                loadedModelName = modelFile.name
                Log.i(TAG, "Model loaded on CPU: ${modelFile.name}")
                true
            } catch (cpuEx: Exception) {
                Log.e(TAG, "Failed to load model on CPU: ${cpuEx.message}")
                loadedModelName = null
                false
            }
        }
    }

    /**
     * Generate a response for the given prompt with streaming token callbacks.
     *
     * @param prompt  The full prompt string (pre-formatted with system/user turns).
     * @param onToken Called for each generated token (for streaming UI updates).
     * @return        The complete generated response string.
     */
    suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val inference = llmInference
            ?: return@withContext buildNoModelMessage()

        Log.d(TAG, "Generating response, prompt length: ${prompt.length}")

        return@withContext suspendCancellableCoroutine { continuation ->
            val responseBuilder = StringBuilder()
            var hasResumed = false

            try {
                inference.generateResponseAsync(
                    prompt,
                    { partialResult, done ->
                        if (partialResult != null) {
                            responseBuilder.append(partialResult)
                            onToken(partialResult)
                        }

                        if (done && !hasResumed) {
                            hasResumed = true
                            continuation.resume(responseBuilder.toString().trim())
                        }
                    }
                )
            } catch (e: Exception) {
                if (!hasResumed) {
                    hasResumed = true
                    Log.e(TAG, "Generation failed: ${e.message}")
                    continuation.resumeWithException(e)
                }
            }

            continuation.invokeOnCancellation {
                // MediaPipe doesn't expose cancel() in 0.10.14 — log and ignore
                Log.d(TAG, "Generation coroutine cancelled")
            }
        }
    }

    /**
     * Synchronous generate (blocking) — prefer [generate] for UI-facing calls.
     */
    suspend fun generateSync(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext buildNoModelMessage()

        return@withContext try {
            inference.generateResponse(prompt)?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Sync generation failed: ${e.message}")
            "❌ Generation error: ${e.message}"
        }
    }

    /** Frees model resources. Call when the app goes to background or model is swapped. */
    fun closeModel() {
        llmInference?.close()
        llmInference = null
        loadedModelName = null
        Log.i(TAG, "Model closed")
    }

    private fun buildNoModelMessage(): String {
        return """
            📱 **No Local Model Loaded**
            
            Download a GGUF model to use 100% offline AI:
            
            1. Go to ⚙️ Settings → Local Model
            2. Tap **Download** next to any model
            3. Wait for download to complete (~1-2GB)
            4. Return here and chat offline!
            
            **Recommended for OnePlus 13:**
            • Llama 3.2 3B (~2GB) — best quality
            • Phi-3.5 Mini (~2.2GB) — fastest
            • Llama 3.2 1B (~1GB) — smallest
        """.trimIndent()
    }
}
