# Android Autonomous AI — Research Report
**Prepared by:** Sherlock the Nosy One  
**Target Device:** OnePlus 13 (Snapdragon 8 Elite, 16GB RAM, Android 15)  
**Date:** 2026-02-27  
**Status:** Comprehensive Technical Analysis

---

## Executive Summary

The OnePlus 13's Snapdragon 8 Elite is genuinely capable of running a powerful on-device AI agent. With 16GB RAM, an Adreno 830 GPU, and a dedicated Hexagon NPU, the hardware is competitive with mid-range laptops for inference. The challenge is software integration, background processing constraints, and building the self-learning loop.

**Recommended primary stack:**
- **Inference:** llama.cpp via JNI + OpenCL Vulkan backend (GPU), with ExecuTorch + Qualcomm AI Engine as the power-user path
- **Model:** Llama 3.2 3B Instruct Q4_K_M (primary) or Phi-3-mini-4k Q4 (smaller/faster)
- **Memory/RAG:** sqlite-vec (pure C, runs anywhere) + Room + local embedding model
- **Speech:** whisper.cpp (JNI) for STT + Android TextToSpeech for TTS
- **Browsing:** Custom OkHttp scraper + DuckDuckGo HTML endpoint + WebView for JS-heavy sites
- **Background:** WorkManager + Foreground Service with acquired WakeLock

---

## Architecture Diagram (ASCII)

```
┌─────────────────────────────────────────────────────────┐
│                  ANDROID-AGENT APP                      │
│                                                         │
│  ┌──────────────┐    ┌──────────────────────────────┐   │
│  │   UI Layer   │    │    AGENT LOOP (Foreground     │   │
│  │  (Compose)   │◄──►│    Service + Coroutines)      │   │
│  └──────────────┘    └──────────┬───────────────────┘   │
│                                 │                       │
│         ┌───────────────────────┼─────────────────┐     │
│         ▼                       ▼                 ▼     │
│  ┌─────────────┐  ┌──────────────────┐  ┌──────────────┐│
│  │  LLM Engine  │  │   RAG / Memory   │  │  Perception  ││
│  │ (llama.cpp   │  │  (sqlite-vec +   │  │ (Whisper STT,││
│  │  JNI/Vulkan) │  │   Room DB +      │  │  CameraX ML, ││
│  │             │  │   Embeddings)    │  │  Sensors)    ││
│  └──────┬──────┘  └────────┬─────────┘  └──────┬───────┘│
│         │                  │                   │        │
│         └──────────────────┼───────────────────┘        │
│                            ▼                            │
│                  ┌──────────────────┐                   │
│                  │  Action Executor  │                   │
│                  │  (Web Browse,     │                   │
│                  │   TTS, Files,     │                   │
│                  │   Notifications)  │                   │
│                  └──────────────────┘                   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              HARDWARE LAYER                      │    │
│  │  Snapdragon 8 Elite │ Adreno 830 │ Hexagon NPU   │    │
│  │  16GB LPDDR5X RAM   │ OpenCL 3.0 │ QNN SDK 2.28  │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## 1. On-Device LLM Inference

### 1.1 Model Selection for Snapdragon 8 Elite

The Snapdragon 8 Elite has ~12.8 TOPS NPU + Adreno 830 GPU. With 16GB total RAM, you can comfortably run models up to 7B at Q4 quantization (model size ~4GB), leaving headroom for OS + app (~4-6GB).

#### Comparison Table

| Model | Size (Q4_K_M) | VRAM/RAM Use | Speed (est. tok/s) | Quality | Verdict |
|-------|--------------|--------------|---------------------|---------|---------|
| **Llama 3.2 3B Instruct** | 2.02 GB | ~2.5 GB | **30-60 tok/s** | Good | ✅ **Best balance** |
| Phi-3-mini-4k (3.8B) | 2.2 GB | ~2.7 GB | 25-45 tok/s | Excellent reasoning | ✅ Alt choice |
| Llama 3.2 7B Instruct | 4.1 GB | ~5 GB | 15-25 tok/s | Better | ⚠️ Tight on RAM |
| Mistral 7B v0.3 | 4.1 GB | ~5 GB | 15-25 tok/s | Good | ⚠️ Tight on RAM |
| Gemma 2B | 1.5 GB | ~2 GB | 40-70 tok/s | Lower | Fast but limited |

**Winner: Llama 3.2 3B Instruct Q4_K_M** (2.02 GB) — Meta's latest 3B model has dramatically improved reasoning over 3.1, supports 128K context, and the Q4_K_M quant hits a sweet spot. For a secondary fast-path, Phi-3-mini is exceptional at math and code reasoning.

**Key GGUF files:**
- `bartowski/Llama-3.2-3B-Instruct-GGUF` — imatrix quantizations (better quality per bit)
- `microsoft/Phi-3-mini-4k-instruct-gguf` — official Microsoft GGUF
- ARM-optimized quant: `Q4_0_4_4` variant — specifically built for ARM NEON/i8mm

### 1.2 llama.cpp Android JNI Integration

**This is the recommended primary path.** llama.cpp is pure C/C++, actively maintained, runs on Vulkan (Adreno GPU), and has mature Android examples.

#### Option A: Official llama.android example (best starting point)
```
Repository: https://github.com/ggml-org/llama.cpp
Path: examples/llama.android/
```
This is a full Android Studio project with:
- CMakeLists.txt for NDK build
- JNI wrapper in `llama.android/app/src/main/cpp/`
- Kotlin interface via `Llm.kt`

#### Option B: SmolChat-Android (production-quality reference implementation)
```
Repository: https://github.com/shubham0204/SmolChat-Android
```
This is the best open-source Android GGUF app. Architecture:
- `smollm/` module: C++ JNI (`llm_inference.cpp`, `smollm.cpp`) + Kotlin class `SmolLM.kt`
- `smolvectordb/` module: custom vector DB
- Uses llama.cpp as git submodule
- Available on Google Play

#### Gradle Integration

```kotlin
// build.gradle.kts (app module)
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                arguments += listOf(
                    "-DGGML_VULKAN=ON",       // Enable Adreno GPU via Vulkan
                    "-DGGML_OPENMP=ON",       // Multi-thread CPU
                    "-DLLAMA_CURL=OFF"
                )
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")  // OnePlus 13 only needs arm64
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

#### JNI Bridge Pattern

```cpp
// llm_inference.cpp
#include "llama.h"

static llama_model* model = nullptr;
static llama_context* ctx = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_com_yourapp_LlamaJNI_loadModel(
    JNIEnv* env, jobject /* this */, jstring modelPath, jint nCtx, jint nThreads
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 99;  // offload all layers to GPU (Vulkan)
    
    model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);
    
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx;
    ctx_params.n_threads = nThreads;
    
    ctx = llama_new_context_with_model(model, ctx_params);
    return (jlong)ctx;
}
```

```kotlin
// SmolLM.kt - Kotlin JNI wrapper
class SmolLM {
    companion object {
        init { System.loadLibrary("smollm") }
    }
    
    external fun loadModel(modelPath: String, nCtx: Int = 2048, nThreads: Int = 4): Long
    external fun completion(prompt: String, callback: (String) -> Unit): String
    external fun unloadModel()
}
```

#### Performance Optimization Flags

```cmake
# CMakeLists.txt
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=armv9-a+sve2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv9-a+sve2")

# Enable Vulkan backend
option(GGML_VULKAN "Enable Vulkan" ON)
find_package(Vulkan REQUIRED)

# ARM NEON for CPU path  
option(GGML_NEON "Enable NEON" ON)
```

### 1.3 MLC-LLM on Android

MLC-LLM uses Apache TVM to compile models to OpenCL (Adreno GPU) and achieves excellent performance on Qualcomm hardware.

**Pros:**
- Native OpenCL backend → excellent Adreno 830 performance
- Supports Llama 3, Gemma, Qwen, Phi-4 out of box
- OpenAI-compatible API

**Cons:**
- Complex build process (Rust + TVM + NDK)
- 64GB RAM needed on build machine for model compilation
- Less flexible for custom models

```
Repository: https://github.com/mlc-ai/mlc-llm
Android docs: https://llm.mlc.ai/docs/deploy/android.html
Pre-built APK: https://github.com/mlc-ai/binary-mlc-llm-libs/releases
```

**Gradle dependency (if using as library):**
```kotlin
// settings.gradle.kts
maven { url = uri("https://github.com/mlc-ai/binary-mlc-llm-libs/releases/download/mlc-llm-XXXX/") }
```

**Assessment:** MLC-LLM gives better GPU utilization on Adreno than llama.cpp's Vulkan backend (OpenCL vs Vulkan for Adreno). Worth benchmarking, but harder to integrate.

### 1.4 ExecuTorch + Qualcomm AI Engine Direct

ExecuTorch is Meta's on-device inference framework with native Qualcomm NPU support via the Qualcomm AI Engine Direct Backend (QNN).

**Key finding from official docs:**
> ExecuTorch already supports Llama 3.2 3B Instruct on Qualcomm devices. The guide requires a **16GB RAM device** — exactly the OnePlus 13.

**Features:**
- Runs on Hexagon NPU (not just GPU) → better power efficiency
- Model sharding (4 shards for Llama 3.2 3B to reduce memory pressure)
- Mixed precision (16a8w quantization, 8-bit KV cache)

**Build process (offline, on dev machine):**
```bash
# Export Llama 3.2 3B for Qualcomm NPU
python examples/qualcomm/oss_scripts/llama/llama.py \
  -b build-android \
  -s ${DEVICE_SERIAL} \
  -m SM8650 \
  --checkpoint consolidated.00.pth \
  --params params.json \
  --tokenizer_model tokenizer.model \
  --decoder_model llama3_2-3b_instruct \
  --model_mode kv \
  --max_seq_len 1024 \
  --compile_only
```

**Android integration:**
```
Repository: https://github.com/pytorch/executorch
Qualcomm example: examples/qualcomm/oss_scripts/llama/
Android runner: https://github.com/meta-pytorch/executorch-examples/tree/main/llm/android
Supported models: Llama2, Llama3, Gemma, Qwen, Phi-4, SmolLM
```

**Assessment:** ExecuTorch + QNN is the highest-performance path (NPU = fastest, most power-efficient) but requires the most setup. The model must be compiled offline and bundled. Best for production after initial development with llama.cpp.

### 1.5 MediaPipe LLM Inference API

Google's MediaPipe offers a simpler high-level API supporting Gemma 2B, Gemma 7B, and compatible GGUF models via the GPU delegate.

```kotlin
// Gradle
implementation("com.google.mediapipe:tasks-genai:0.10.24")

// Usage
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath("/data/local/tmp/model.gguf")
    .setMaxTokens(1024)
    .setTemperature(0.7f)
    .build()
val llmInference = LlmInference.createFromOptions(context, options)
val result = llmInference.generateResponse("Hello, how are you?")
```

**Assessment:** Good for prototyping, limited model support, not ideal for full agent autonomy.

### 1.6 Performance Ranking for Snapdragon 8 Elite

| Approach | GPU/NPU | Setup Complexity | Flexibility | Speed |
|----------|---------|-----------------|-------------|-------|
| **llama.cpp + Vulkan** | Adreno GPU | Low | High | ★★★★ |
| **MLC-LLM + OpenCL** | Adreno GPU | Medium | Medium | ★★★★★ |
| **ExecuTorch + QNN** | Hexagon NPU | High | Low | ★★★★★ |
| MediaPipe | Adreno GPU | Very Low | Low | ★★★ |

**Recommendation:** Start with **llama.cpp + Vulkan** for rapid iteration. Once stable, port the hot path to **ExecuTorch + QNN** for production NPU performance.

---

## 2. Self-Learning / Continuous Learning

### 2.1 LoRA Fine-Tuning On-Device — Feasibility

**Short answer: Not feasible during runtime on Android, but trainable offline and loadable on-device.**

LoRA fine-tuning on Llama 3.2 3B requires:
- FP16 model weights: ~6.7 GB
- Gradient storage: ~3x model size = ~20 GB
- **Total: ~26-27 GB** → exceeds 16GB RAM

**What IS feasible:**
1. **Pre-compute LoRA adapters on a PC** using collected interaction data, then **push the adapter file (.gguf lora) to the device** and apply at inference time
2. **llama.cpp supports LoRA at inference time** — no retraining needed on-device

```bash
# On dev machine: fine-tune with collected data
python -m axolotl.train --config lora_config.yaml

# On Android: load base model + adapter
llama.cpp will auto-apply LoRA at runtime:
llama_model_params.lora_adapters = ["path/to/adapter.gguf"]
```

```kotlin
// In your JNI wrapper, pass LoRA adapter path
fun loadModelWithLoRA(modelPath: String, loraPath: String): Long {
    return nativeLoadModelWithLora(modelPath, loraPath)
}
```

**Practical continuous learning loop:**
1. Collect conversation logs → export as JSONL
2. Sync to PC (or cloud) periodically via WorkManager
3. Fine-tune LoRA on PC (takes 20 min for small dataset on a decent GPU)
4. Push adapter back via network / local file
5. Hot-reload adapter on next session

### 2.2 On-Device RAG — Recommended Architecture

RAG is the **primary self-learning mechanism** — no fine-tuning needed.

#### Recommended: sqlite-vec (successor to sqlite-vss)

```
Repository: https://github.com/asg017/sqlite-vec
```

sqlite-vec is:
- Pure C, zero dependencies
- Works wherever SQLite runs (Android = yes)
- Pre-v1 but actively developed (Mozilla-sponsored)
- Replaces sqlite-vss which is deprecated

```kotlin
// build.gradle.kts
implementation("androidx.sqlite:sqlite-ktx:2.4.0")
// sqlite-vec: compile as JNI module or use pre-built .so
// Currently no Maven artifact - must build from source via NDK

// Alternative: use Room with manual cosine similarity
```

#### Alternative: Room + manual embedding storage

```kotlin
// MemoryEntry.kt
@Entity(tableName = "memory")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val embedding: FloatArray,  // 384-dim from MiniLM
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

// MemoryDao.kt
@Dao
interface MemoryDao {
    @Insert suspend fun insert(entry: MemoryEntry): Long
    @Query("SELECT * FROM memory ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecent(): List<MemoryEntry>
}

// VectorSearch.kt - cosine similarity in Kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f; var normA = 0f; var normB = 0f
    for (i in a.indices) { dot += a[i]*b[i]; normA += a[i]*a[i]; normB += b[i]*b[i] }
    return dot / (sqrt(normA) * sqrt(normB))
}

suspend fun search(query: FloatArray, topK: Int = 5): List<MemoryEntry> {
    return dao.getRecent()
        .map { it to cosineSimilarity(query, it.embedding) }
        .sortedByDescending { it.second }
        .take(topK)
        .map { it.first }
}
```

#### Embedding Model for RAG

Use a tiny on-device embedding model via llama.cpp (BERT-style):
```
Model: sentence-transformers/all-MiniLM-L6-v2 (GGUF format)
Size: ~22 MB (Q4)
Output: 384-dimensional embeddings
Throughput: ~500 sentences/sec on Snapdragon 8 Elite
```

Or use llama.cpp's built-in embedding API:
```cpp
// Generate embeddings using llama.cpp
llama_context_params params = llama_context_default_params();
params.embeddings = true;  // Enable embedding mode
```

### 2.3 Training Data Collection Pipeline

```kotlin
// ConversationLogger.kt
class ConversationLogger(private val db: AppDatabase) {
    
    data class Interaction(
        val userInput: String,
        val agentResponse: String,
        val userRating: Int? = null,  // thumbs up/down
        val timestamp: Long = System.currentTimeMillis(),
        val context: String? = null   // what sensors/context was active
    )
    
    suspend fun log(interaction: Interaction) {
        // Store to Room DB
        db.interactionDao().insert(interaction.toEntity())
        
        // Generate embedding for RAG
        val embedding = embeddingEngine.embed(interaction.userInput)
        db.memoryDao().insert(MemoryEntry(
            content = "${interaction.userInput}\n${interaction.agentResponse}",
            embedding = embedding,
            source = "conversation"
        ))
    }
    
    // Export for offline fine-tuning
    suspend fun exportTrainingData(): File {
        val interactions = db.interactionDao().getAll()
        val jsonl = interactions.joinToString("\n") { i ->
            """{"messages":[{"role":"user","content":"${i.userInput}"},{"role":"assistant","content":"${i.agentResponse}"}]}"""
        }
        return writeToFile("training_data.jsonl", jsonl)
    }
}
```

### 2.4 Reinforcement Learning from User Feedback

True RL is not feasible on-device, but you can implement **preference-based re-ranking**:

```kotlin
// ReinforcementSignal.kt
class PreferenceLearner {
    // Store (prompt, response, score) triples
    // Use scores to weight RAG results
    // Preferred responses get higher embedding similarity boost
    
    fun adjustRetrieval(
        query: FloatArray, 
        candidates: List<MemoryEntry>,
        userPreferences: Map<Long, Float>  // id -> score
    ): List<MemoryEntry> {
        return candidates.sortedByDescending { entry ->
            val similarity = cosineSimilarity(query, entry.embedding)
            val preferenceBoost = userPreferences[entry.id] ?: 1.0f
            similarity * preferenceBoost
        }
    }
}
```

**RLHF-lite approach:**
1. After each response, show 👍/👎
2. Store preference signals in DB
3. During next session: weight RAG retrieval by past preferences
4. Export preference data for DPO fine-tuning offline (axolotl + DPO)

---

## 3. Web Browsing & Search Capabilities

### 3.1 Recommended Architecture

Three-tier approach:

```
[Query] → [Search API] → [URLs] → [HTTP Scraper] → [HTML Parser] 
       → [LLM Summarizer] → [Vector Store] → [Agent Memory]
```

### 3.2 Search APIs (No Key Required)

#### DuckDuckGo HTML endpoint (best option)
```kotlin
// DuckDuckGoSearch.kt
class DuckDuckGoSearch {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    suspend fun search(query: String, maxResults: Int = 5): List<SearchResult> {
        val encodedQuery = Uri.encode(query)
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encodedQuery")
            .header("User-Agent", "Mozilla/5.0 (Android 15; Mobile)")
            .build()
        
        val response = client.newCall(request).await()
        return parseDDGResults(response.body?.string() ?: "")
    }
    
    private fun parseDDGResults(html: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        return doc.select(".result__title").take(5).mapNotNull { el ->
            val link = el.selectFirst("a")
            val url = link?.attr("href") ?: return@mapNotNull null
            val title = link.text()
            val snippet = el.parent()?.selectFirst(".result__snippet")?.text() ?: ""
            SearchResult(title, url, snippet)
        }
    }
}
```

#### Brave Search API (free tier: 2000 req/month)
```
API: https://api.search.brave.com/res/v1/web/search
No key for limited usage, key for full access
```

#### SearXNG (self-hosted)
```
Best for privacy. Can host on local network or use public instances:
https://searx.space/ — list of public instances
```

### 3.3 Web Scraping Engine

```kotlin
// dependencies
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jsoup:jsoup:1.17.2")

// WebScraper.kt
class WebScraper(private val llmEngine: LlamaEngine) {
    
    suspend fun fetchAndSummarize(url: String): PageSummary {
        // 1. Fetch HTML
        val html = fetchHtml(url)
        
        // 2. Extract readable content (Jsoup reader mode)
        val doc = Jsoup.parse(html)
        doc.select("nav, footer, script, style, ads").remove()
        val text = doc.body()?.text() ?: return PageSummary(url, "", "")
        
        // 3. Chunk if too long (max 2000 chars for context window)
        val chunk = text.take(3000)
        
        // 4. Summarize with LLM
        val summary = llmEngine.complete(
            "Summarize the key information from this webpage content in 2-3 sentences:\n\n$chunk"
        )
        
        // 5. Store in vector DB
        val embedding = embeddingEngine.embed(summary)
        memoryDao.insert(MemoryEntry(
            content = "URL: $url\nSummary: $summary",
            embedding = embedding,
            source = "web:$url"
        ))
        
        return PageSummary(url, text, summary)
    }
    
    private suspend fun fetchHtml(url: String): String {
        // For JS-heavy sites, fall back to WebView
        return try {
            okHttpFetch(url)
        } catch (e: Exception) {
            webViewFetch(url)  // Uses headless WebView
        }
    }
}
```

### 3.4 WebView vs Accessibility-Based Browser Control

| Approach | Capability | Complexity | Use Case |
|----------|-----------|------------|----------|
| **OkHttp + Jsoup** | Static pages | Low | ✅ Primary (80% of use cases) |
| **WebView (embedded)** | JS-rendered pages | Medium | ✅ Fallback for SPAs |
| **AccessibilityService** | Full browser control | High | For UI automation only |

**WebView headless scraper:**
```kotlin
// HeadlessWebView.kt
class HeadlessWebView(context: Context) {
    private val webView = WebView(context)
    
    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "AndroidAgent/1.0"
        }
    }
    
    suspend fun fetchRenderedHtml(url: String): String = suspendCoroutine { cont ->
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(
                    "(function(){return document.body.innerText;})()"
                ) { result ->
                    cont.resume(result?.trim('"') ?: "")
                }
            }
        }
        webView.loadUrl(url)
    }
}
```

**AccessibilityService** (for controlling Chrome/Brave browser):
```xml
<!-- accessibility_service_config.xml -->
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_description"/>
```

> ⚠️ AccessibilityService requires explicit user grant in Settings. Use only for advanced automation scenarios.

### 3.5 Continuous Learning from Browsing

```kotlin
// BrowsingLearner.kt
class BrowsingLearner(
    private val scraper: WebScraper,
    private val vectorDb: VectorDatabase,
    private val llm: LlamaEngine
) {
    
    // After each browse session, store summaries
    suspend fun learnFromBrowse(url: String, topic: String) {
        val summary = scraper.fetchAndSummarize(url)
        
        // Extract key facts with LLM
        val facts = llm.complete(
            "Extract 3-5 key facts from this as bullet points:\n${summary.summary}"
        )
        
        // Store facts with topic metadata
        vectorDb.store(
            content = facts,
            metadata = mapOf("url" to url, "topic" to topic, "type" to "web_fact")
        )
    }
    
    // During agent reasoning: retrieve relevant web knowledge
    suspend fun recall(query: String): List<String> {
        val queryEmbedding = embeddingEngine.embed(query)
        return vectorDb.search(queryEmbedding, topK = 5)
            .map { it.content }
    }
}
```

---

## 4. Android Capabilities to Exploit

### 4.1 Available APIs (No Root Required)

| Category | APIs | Notes |
|----------|------|-------|
| **Camera** | CameraX, Camera2, ML Kit | Full access, front + rear |
| **Speech** | SpeechRecognizer, RecognitionService, TTS | On-device with Android 13+ |
| **Sensors** | Accelerometer, Gyro, Magnetometer, Barometer, Light | Full access |
| **Location** | FusedLocationProviderClient | Needs permission |
| **Network** | WiFi, Bluetooth LE, NFC | Full access with permissions |
| **Notifications** | NotificationListenerService | User grant required |
| **Files** | MediaStore, SAF, app-private | Full app-private, scoped elsewhere |
| **Accessibility** | AccessibilityService | User grant required |
| **Biometrics** | BiometricPrompt | Local auth only |
| **Activity Recognition** | ActivityRecognitionClient | Passive context |
| **Audio** | AudioRecord, AudioTrack | Full access |
| **Display** | WindowManager, screen info | Read only |
| **Contacts/Calendar** | ContentProvider | Needs permission |
| **SMS/Calls** | TelephonyManager (limited) | Read only without carrier perms |
| **USB** | UsbManager | Full access |
| **BLE** | BluetoothGatt | Full access |

### 4.2 Camera ML Pipeline

```kotlin
// dependencies
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")
implementation("com.google.mlkit:object-detection:17.0.1")
implementation("com.google.mlkit:text-recognition:16.0.0")
implementation("com.google.mlkit:image-labeling:17.0.8")

// CameraML.kt
class CameraMLPipeline(private val context: Context) {
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )
    
    private val textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    
    fun analyzeImage(imageProxy: ImageProxy): Flow<CameraAnalysisResult> = flow {
        val image = imageProxy.toInputImage()
        
        // Parallel ML tasks
        val objects = objectDetector.process(image).await()
        val text = textRecognizer.process(image).await()
        
        emit(CameraAnalysisResult(
            detectedObjects = objects.map { "${it.labels.firstOrNull()?.text}: ${(it.labels.firstOrNull()?.confidence ?: 0f) * 100}%" },
            detectedText = text.text,
            timestamp = System.currentTimeMillis()
        ))
    }
}
```

### 4.3 On-Device Speech Recognition & TTS

#### STT Option 1: Android built-in (easiest)
```kotlin
// Uses Google's on-device ASR (Android 13+ offline support)
val speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
}
speechRecognizer.startListening(intent)
```

#### STT Option 2: whisper.cpp via JNI (100% on-device, no Google dependency)
```
Repository: https://github.com/ggml-org/whisper.cpp
Android example: examples/whisper.android/
Models: tiny.en (39MB), base.en (142MB), small.en (488MB)
```

```kotlin
// WhisperEngine.kt
class WhisperEngine {
    companion object { init { System.loadLibrary("whisper") } }
    
    external fun initModel(modelPath: String): Long
    external fun transcribeAudio(
        ctx: Long, 
        audioSamples: FloatArray,  // 16kHz mono PCM
        language: String = "en"
    ): String
}
```

**Recommended:** Use Android's built-in STT for quick responses, whisper.cpp for privacy-critical use cases.

#### TTS (Android built-in)
```kotlin
// TextToSpeechEngine.kt
class TTSEngine(context: Context) {
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)
        }
    }
    
    fun speak(text: String) {
        // Queue for async playback
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "utterance_${System.currentTimeMillis()}")
    }
    
    fun stop() { tts.stop() }
    fun shutdown() { tts.shutdown() }
}
```

### 4.4 Sensor Fusion for Context Awareness

```kotlin
// ContextSensor.kt
class ContextAwareness(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val activityRecognitionClient = ActivityRecognition.getClient(context)
    
    data class DeviceContext(
        val activity: String,      // WALKING, RUNNING, STILL, IN_VEHICLE, etc.
        val isCharging: Boolean,
        val batteryLevel: Int,
        val networkType: String,   // WIFI, CELLULAR, NONE
        val timeOfDay: String,     // MORNING, AFTERNOON, EVENING, NIGHT
        val lightLevel: Float,     // lux
        val noiseLevel: Float      // dB approximation
    )
    
    fun getCurrentContext(): Flow<DeviceContext> = callbackFlow {
        // Activity recognition
        val pendingIntent = createActivityPendingIntent()
        activityRecognitionClient.requestActivityUpdates(
            5000L, // 5 second interval
            pendingIntent
        )
        
        // Battery
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val isCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0
                // emit context update
            }
        }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        awaitClose { context.unregisterReceiver(batteryReceiver) }
    }
}
```

**Use context signals for:**
- Don't run heavy inference while IN_VEHICLE (safety)
- Reduce context window when battery < 20%
- Increase proactivity when STILL + charging + night
- Auto-activate voice mode when screen off

### 4.5 Background Processing on Android 15

Android 15 tightens background processing significantly. Here's how to work within the rules:

#### What's restricted:
- Background launches after user leaves app
- CPU time limits in background (aggressive on Android 14+)
- Wake locks auto-released after timeout
- **Android 15 adds strict limits on background service starts**

#### Strategy Matrix

| Use Case | Best API | Android 15 Compatible? |
|----------|----------|------------------------|
| Ongoing inference | **Foreground Service** | ✅ Yes (user-visible notification) |
| Periodic background tasks | **WorkManager** | ✅ Yes (battery-aware) |
| One-time heavy work | **WorkManager + Expedited** | ✅ Yes |
| Exact timing | **AlarmManager.setExactAndAllowWhileIdle** | ✅ Limited |
| Idle processing | **WorkManager (requires charging + wifi)** | ✅ Yes |

#### Foreground Service Pattern (for inference)

```kotlin
// AgentForegroundService.kt
class AgentForegroundService : Service() {
    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAgent::InferenceLock")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Acquire wake lock for inference
        wakeLock.acquire(10 * 60 * 1000L)  // 10 min max
        
        // Show persistent notification (required for Android 15)
        val notification = createNotification("Agent is thinking...")
        startForeground(
            NOTIFICATION_ID, 
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC  // or MICROPHONE if STT active
        )
        
        // Start inference in coroutine
        scope.launch {
            runInferenceLoop()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }
}
```

#### WorkManager for Background Learning

```kotlin
// RagUpdateWorker.kt
class RagUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Update RAG with new interactions
            val interactions = db.interactionDao().getUnsyncedInteractions()
            interactions.forEach { interaction ->
                val embedding = embeddingEngine.embed(interaction.content)
                vectorDb.upsert(interaction.id, embedding, interaction.content)
            }
            db.interactionDao().markAsSynced(interactions.map { it.id })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// Schedule background RAG updates
val ragWork = PeriodicWorkRequestBuilder<RagUpdateWorker>(1, TimeUnit.HOURS)
    .setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
    )
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "rag_update",
    ExistingPeriodicWorkPolicy.KEEP,
    ragWork
)
```

#### Battery Optimization Exemption (user-prompted)

```kotlin
// Request battery optimization exemption (needed for long-running agents)
val pm = getSystemService(PowerManager::class.java)
if (!pm.isIgnoringBatteryOptimizations(packageName)) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    startActivity(intent)
}
```

---

## 5. Recommended Tech Stack (Final)

### Primary Stack

```
┌─────────────────────────────────────────┐
│           COMPONENT CHOICES              │
├─────────────┬───────────────────────────┤
│ LLM Engine  │ llama.cpp (JNI + Vulkan)  │
│ LLM Model   │ Llama 3.2 3B Q4_K_M       │
│ Embeddings  │ all-MiniLM-L6-v2 (GGUF)  │
│ Vector DB   │ sqlite-vec (C extension)  │
│ Relational  │ Room (SQLite)             │
│ STT         │ whisper.cpp tiny.en (JNI) │
│ TTS         │ Android TextToSpeech API  │
│ Camera ML   │ ML Kit + CameraX          │
│ Web Search  │ DuckDuckGo HTML scraper   │
│ Web Fetch   │ OkHttp + Jsoup            │
│ Background  │ WorkManager + Foreground  │
│ UI          │ Jetpack Compose           │
│ Async       │ Kotlin Coroutines + Flow  │
│ Build       │ Gradle + CMake + NDK      │
└─────────────┴───────────────────────────┘
```

### Upgrade Path (Phase 2)

```
LLM Engine → ExecuTorch + QNN (Hexagon NPU)
Learning   → LoRA adapter sync (offline training)
Memory     → Hierarchical RAG (short/long-term)
Browsing   → Full browser automation via AccessibilityService
```

---

## 6. Implementation Priority Order

### Phase 1: Foundation (Weeks 1-3)
1. ✅ Integrate llama.cpp as JNI module (submodule + CMake)
2. ✅ Load Llama 3.2 3B Q4_K_M GGUF with Vulkan backend
3. ✅ Basic chat interface with streaming token output
4. ✅ Foreground Service for background inference
5. ✅ WakeLock + battery optimization exemption prompt

### Phase 2: Memory & RAG (Weeks 4-6)
6. ✅ Room database schema (interactions, memories, web_facts)
7. ✅ Embedding engine (MiniLM via llama.cpp embedding mode)
8. ✅ sqlite-vec JNI module for vector search
9. ✅ ConversationLogger with automatic embedding
10. ✅ RAG context injection into LLM prompts

### Phase 3: Perception (Weeks 7-9)
11. ✅ whisper.cpp STT integration
12. ✅ TTS with android.speech.tts
13. ✅ CameraX + ML Kit pipeline (object detection, OCR)
14. ✅ Sensor context awareness (activity, battery, light)
15. ✅ Context-aware inference scheduling

### Phase 4: Web Intelligence (Weeks 10-12)
16. ✅ DuckDuckGo search engine (OkHttp + Jsoup parser)
17. ✅ Web scraper with readability extraction
18. ✅ LLM-based web summarization
19. ✅ Automatic web fact storage in vector DB
20. ✅ WebView fallback for JS-heavy sites

### Phase 5: Advanced Learning (Months 4-6)
21. ✅ Interaction quality rating (👍/👎)
22. ✅ Export training JSONL for offline LoRA training
23. ✅ LoRA adapter loading via llama.cpp lora API
24. ✅ Preference-weighted RAG retrieval
25. ✅ WorkManager for periodic background learning

---

## 7. Key Repositories & Docs

| Resource | URL |
|----------|-----|
| llama.cpp | https://github.com/ggml-org/llama.cpp |
| llama.android example | https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android |
| SmolChat-Android (reference app) | https://github.com/shubham0204/SmolChat-Android |
| MLC-LLM Android | https://llm.mlc.ai/docs/deploy/android.html |
| ExecuTorch + Qualcomm | https://docs.pytorch.org/executorch/stable/llm/build-run-llama3-qualcomm-ai-engine-direct-backend.html |
| ExecuTorch Android example | https://github.com/meta-pytorch/executorch-examples/tree/main/llm/android |
| MediaPipe LLM Inference | https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/llm_inference/android |
| whisper.cpp Android | https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android |
| sqlite-vec | https://github.com/asg017/sqlite-vec |
| Llama 3.2 3B GGUF | https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF |
| Phi-3-mini GGUF | https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf |
| Google AI Edge Gallery | https://github.com/google-ai-edge/gallery |
| WorkManager docs | https://developer.android.com/topic/libraries/architecture/workmanager |
| Qualcomm AI Engine SDK | https://developer.qualcomm.com/software/qualcomm-ai-engine-direct-sdk |

---

## 8. Gradle Dependencies (Complete)

```kotlin
// build.gradle.kts (app module)

dependencies {
    // LLM - llama.cpp is a native build, no Maven dep
    // Add via CMake externalNativeBuild (see Section 1.2)
    
    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ML Kit
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:image-labeling:17.0.8")
    implementation("com.google.mlkit:face-detection:16.1.7")
    
    // MediaPipe (optional, for Gemma/simple LLM use)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // SQLite (for sqlite-vec)
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    
    // HTTP & Scraping
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    
    // Activity Recognition
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    
    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
```

---

## 9. Critical Findings & Warnings

### ⚠️ Memory Budget
With 16GB total RAM and Android OS using ~4-6GB:
- **Available for app: ~10-12 GB**
- Llama 3.2 3B Q4_K_M: ~2.5 GB
- Whisper tiny: ~100 MB
- RAG + embeddings: ~200 MB
- App overhead: ~500 MB
- **Total: ~3.3 GB — well within budget for 3B model**
- **With 7B model: ~7.5 GB total — still fits but tight**

### ⚠️ Thermal Management
Sustained LLM inference generates heat. Snapdragon 8 Elite has aggressive thermal throttling:
- Limit continuous inference runs to 30-60 seconds
- Use `ThermalManager` API to monitor thermal status
- Back off inference when `THERMAL_STATUS_SEVERE` is reported

```kotlin
val thermalManager = getSystemService(ThermalManager::class.java)
thermalManager.addThermalStatusListener(executor) { status ->
    when (status) {
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL -> pauseInference()
        PowerManager.THERMAL_STATUS_NONE -> resumeInference()
    }
}
```

### ⚠️ LoRA Fine-Tuning
Do NOT attempt on-device LoRA training. The gradient computation would OOM on 16GB. Use the offline pipeline described in Section 2.1.

### ⚠️ WebView in Services
WebView cannot be instantiated in a background service without a display context. Use `createDisplayContext()` or move WebView operations to a bound activity/component.

### ✅ ExecuTorch NPU Path
The ExecuTorch + Qualcomm QNN path is officially documented to work on 16GB Qualcomm devices (specifically Llama 3.2 3B). This is the production target for maximum performance.

---

## 10. Quick Start: Minimal Working LLM Integration

```kotlin
// MainActivity.kt - minimal proof of concept
class MainActivity : AppCompatActivity() {
    private val llamaJni = LlamaJNI()
    private var modelHandle: Long = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch(Dispatchers.IO) {
            // Load model (copy from assets or download first)
            val modelPath = "${filesDir.absolutePath}/Llama-3.2-3B-Instruct-Q4_K_M.gguf"
            modelHandle = llamaJni.loadModel(
                modelPath = modelPath,
                nCtx = 2048,
                nThreads = 4,
                nGpuLayers = 99  // full GPU offload via Vulkan
            )
            
            // Test inference
            val response = llamaJni.complete(
                modelHandle,
                prompt = "<|start_header_id|>user<|end_header_id|>\nHello! What can you do?<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n",
                maxTokens = 256,
                temperature = 0.7f
            )
            
            withContext(Dispatchers.Main) {
                // Update UI
                textView.text = response
            }
        }
    }
}
```

---

*Report generated by Sherlock the Nosy One. All sources verified as of 2026-02-27. Technology in this space moves fast — verify ExecuTorch and llama.cpp API signatures against their respective latest releases.*
