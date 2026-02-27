package ai.openclaw.androidagent.core

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages downloading GGUF model files for on-device LLM inference.
 *
 * Downloads to: /data/data/ai.openclaw.androidagent/files/models/
 *
 * Uses Android's DownloadManager for reliable background downloading with
 * system notifications and resume-on-reconnect support.
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val HUGGINGFACE_BASE = "https://huggingface.co"

        /** Catalog of supported GGUF models with HuggingFace download URLs. */
        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "llama3.2-3b",
                name = "Llama 3.2 3B Instruct",
                fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                url = "$HUGGINGFACE_BASE/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                sizeBytes = 2_019_377_152L,   // ~1.88 GB
                description = "Best quality. Recommended for OnePlus 13.",
                ramRequiredMb = 4096
            ),
            ModelInfo(
                id = "llama3.2-1b",
                name = "Llama 3.2 1B Instruct",
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                url = "$HUGGINGFACE_BASE/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                sizeBytes = 771_751_936L,    // ~736 MB
                description = "Smallest and fastest. Good for quick tasks.",
                ramRequiredMb = 2048
            ),
            ModelInfo(
                id = "phi3.5-mini",
                name = "Phi-3.5 Mini Instruct",
                fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                url = "$HUGGINGFACE_BASE/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                sizeBytes = 2_393_948_160L,  // ~2.23 GB
                description = "Microsoft model. Excellent reasoning and coding.",
                ramRequiredMb = 4096
            )
        )
    }

    data class ModelInfo(
        val id: String,
        val name: String,
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val description: String,
        val ramRequiredMb: Int
    ) {
        val displaySize: String get() {
            val mb = sizeBytes / 1024 / 1024
            return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"
        }
    }

    data class DownloadState(
        val modelId: String,
        val status: Status,
        val progressPercent: Int = 0,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val errorMessage: String? = null
    ) {
        enum class Status {
            IDLE, QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
        }
    }

    // Per-model download states
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // DownloadManager ID → model ID mapping
    private val activeDownloads = mutableMapOf<Long, String>()

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Returns file for a given model in the models directory. */
    fun getModelFile(model: ModelInfo): File {
        return File(File(context.filesDir, LlamaEngine.MODELS_DIR), model.fileName)
    }

    /** Returns true if model is already fully downloaded. */
    fun isModelDownloaded(model: ModelInfo): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() > 0L &&
                (model.sizeBytes <= 0L || file.length() >= model.sizeBytes * 0.95) // 5% tolerance
    }

    /** Returns download state for a specific model. */
    fun getDownloadState(modelId: String): DownloadState {
        return _downloadStates.value[modelId]
            ?: DownloadState(modelId, DownloadState.Status.IDLE)
    }

    /**
     * Starts downloading a model using Android DownloadManager.
     * Safe to call if already downloaded (returns immediately with COMPLETED state).
     */
    fun startDownload(model: ModelInfo) {
        if (isModelDownloaded(model)) {
            updateState(model.id, DownloadState.Status.COMPLETED, 100)
            return
        }

        // Already downloading?
        val currentState = getDownloadState(model.id)
        if (currentState.status == DownloadState.Status.DOWNLOADING ||
            currentState.status == DownloadState.Status.QUEUED) {
            return
        }

        val modelsDir = File(context.filesDir, LlamaEngine.MODELS_DIR).also { it.mkdirs() }
        val destFile = File(modelsDir, model.fileName)

        // Remove incomplete previous file
        if (destFile.exists() && !isModelDownloaded(model)) {
            destFile.delete()
        }

        Log.i(TAG, "Starting download: ${model.name} from ${model.url}")

        val request = DownloadManager.Request(Uri.parse(model.url)).apply {
            setTitle("Downloading ${model.name}")
            setDescription("${model.displaySize} — OpenClaw AI Model")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destFile))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val downloadId = downloadManager.enqueue(request)
        activeDownloads[downloadId] = model.id
        updateState(model.id, DownloadState.Status.QUEUED, 0)

        Log.i(TAG, "Download queued: id=$downloadId model=${model.id}")
    }

    /**
     * Cancels an active download.
     */
    fun cancelDownload(modelId: String) {
        val downloadId = activeDownloads.entries.firstOrNull { it.value == modelId }?.key
        if (downloadId != null) {
            downloadManager.remove(downloadId)
            activeDownloads.remove(downloadId)
            Log.i(TAG, "Download cancelled: $modelId")
        }
        updateState(modelId, DownloadState.Status.CANCELLED)
    }

    /**
     * Deletes a downloaded model file.
     */
    fun deleteModel(model: ModelInfo) {
        val file = getModelFile(model)
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "Model deleted: ${model.fileName}")
        }
        updateState(model.id, DownloadState.Status.IDLE)
    }

    /**
     * Polls download progress for all active downloads.
     * Call this periodically from the UI (e.g., every second).
     */
    fun pollProgress() {
        if (activeDownloads.isEmpty()) return

        val query = DownloadManager.Query().setFilterById(*activeDownloads.keys.toLongArray())
        val cursor = downloadManager.query(query)

        cursor?.use {
            while (it.moveToNext()) {
                val idIdx = it.getColumnIndex(DownloadManager.COLUMN_ID)
                val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                if (idIdx < 0) return@use

                val downloadId = it.getLong(idIdx)
                val modelId = activeDownloads[downloadId] ?: continue
                val dmStatus = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                val downloaded = if (bytesDownloadedIdx >= 0) it.getLong(bytesDownloadedIdx) else 0L
                val total = if (bytesTotalIdx >= 0) it.getLong(bytesTotalIdx) else 0L

                val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0

                when (dmStatus) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        updateState(
                            modelId,
                            if (dmStatus == DownloadManager.STATUS_RUNNING)
                                DownloadState.Status.DOWNLOADING
                            else
                                DownloadState.Status.QUEUED,
                            progress, downloaded, total
                        )
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        updateState(modelId, DownloadState.Status.PAUSED, progress, downloaded, total)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        activeDownloads.remove(downloadId)
                        // Verify file
                        val model = AVAILABLE_MODELS.find { m -> m.id == modelId }
                        if (model != null && !isModelDownloaded(model)) {
                            Log.e(TAG, "Downloaded file verification failed: ${model.fileName}")
                            updateState(modelId, DownloadState.Status.FAILED,
                                errorMessage = "File verification failed. Please retry.")
                        } else {
                            Log.i(TAG, "Download complete: $modelId")
                            updateState(modelId, DownloadState.Status.COMPLETED, 100, total, total)
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        activeDownloads.remove(downloadId)
                        val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIdx >= 0) it.getInt(reasonIdx) else -1
                        Log.e(TAG, "Download failed: $modelId reason=$reason")
                        updateState(modelId, DownloadState.Status.FAILED,
                            errorMessage = "Download failed (code $reason). Check your internet connection.")
                    }
                }
            }
        }
    }

    /**
     * Returns total storage used by all downloaded models.
     */
    fun totalStorageUsedBytes(): Long {
        return File(context.filesDir, LlamaEngine.MODELS_DIR)
            .listFiles { f -> f.name.endsWith(".gguf") }
            ?.sumOf { it.length() } ?: 0L
    }

    /** Human-readable storage used. */
    fun totalStorageUsedDisplay(): String {
        val bytes = totalStorageUsedBytes()
        val mb = bytes / 1024 / 1024
        return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"
    }

    private fun updateState(
        modelId: String,
        status: DownloadState.Status,
        progressPercent: Int = 0,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
        errorMessage: String? = null
    ) {
        val current = _downloadStates.value.toMutableMap()
        current[modelId] = DownloadState(
            modelId = modelId,
            status = status,
            progressPercent = progressPercent,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            errorMessage = errorMessage
        )
        _downloadStates.value = current
    }

    /** Initialize states for already-downloaded models at startup. */
    fun initializeStates() {
        val states = AVAILABLE_MODELS.associate { model ->
            model.id to DownloadState(
                modelId = model.id,
                status = if (isModelDownloaded(model))
                    DownloadState.Status.COMPLETED
                else
                    DownloadState.Status.IDLE
            )
        }
        _downloadStates.value = states
    }
}
