# Project Handoff: Android Agent

**Created:** February 27, 2026  
**Agent:** Thor ⚡  
**For:** Ash  

---

## 🎉 What's Been Built

A complete Android agent framework — think OpenClaw, but for Android devices. It uses accessibility services and optional root access to automate and control your phone through AI.

### Project Structure

```
android-agent/
├── .github/workflows/build.yml   # Auto-builds APK on push
├── app/
│   ├── src/main/
│   │   ├── kotlin/
│   │   │   ├── core/             # AgentCore - main runtime
│   │   │   ├── services/         # Accessibility & background services
│   │   │   ├── tools/            # 5 built-in tools
│   │   │   ├── ui/               # Jetpack Compose chat interface
│   │   │   └── models/           # Data models (Message, Tool, etc.)
│   │   ├── res/                  # Android resources
│   │   └── AndroidManifest.xml   # Permissions & services
│   └── build.gradle.kts          # App dependencies
├── docs/                         # Comprehensive docs
│   ├── ARCHITECTURE.md           # System design
│   ├── TOOLS.md                  # Tool reference
│   ├── SETUP.md                  # Installation guide
│   └── CONTRIBUTING.md           # Contribution guide
├── gradle/                       # Gradle wrapper config
├── README.md                     # Main project readme
├── LICENSE                       # MIT License
└── PROJECT_STATUS.md             # Current status & roadmap
```

---

## ✅ What Works Right Now

### 1. **Accessibility-Based Control**
- Tap at any coordinate
- Swipe gestures
- Type text into fields
- Read screen hierarchy (all UI elements as JSON)

### 2. **Root Enhancement** (optional)
- Execute shell commands as root
- Direct input injection
- System-level automation

### 3. **Tool System**
- 5 working tools: `tap`, `swipe`, `type_text`, `screenshot`, `shell`
- Easy to add more tools (see docs/TOOLS.md)

### 4. **Modern UI**
- Clean chat interface (Jetpack Compose)
- Message history
- User vs Agent message bubbles
- Loading states

### 5. **CI/CD Ready**
- GitHub Actions workflow
- Auto-builds APK on every push
- Artifact upload (30-day retention)
- Release creation on git tags

---

## 🚧 What's Missing (Next Steps)

### Critical (Do First)

1. **LLM Integration** — Currently just echoes placeholder text
   - Add Retrofit HTTP client
   - Implement OpenAI-compatible API calls
   - Parse function/tool calls from LLM responses
   - See `AgentCore.kt` — search for `TODO: Implement actual LLM call`

2. **Settings Screen** — No way to configure LLM endpoint yet
   - Add Settings button action in ChatScreen.kt
   - Create SettingsScreen.kt (Compose)
   - Save preferences (use DataStore or SharedPreferences)

3. **App Icons** — Using default placeholders
   - Design launcher icon
   - Generate mipmaps (use Android Asset Studio)
   - Place in `app/src/main/res/mipmap-*/`

### Important (Do Soon)

4. **Actual Screenshot** — Current "screenshot" returns hierarchy JSON, not pixels
   - Implement MediaProjection API
   - Capture pixel screenshot as PNG
   - Encode as base64 for vision models

5. **Persistence** — Conversations lost on restart
   - Set up Room database
   - Save/restore message history
   - Export/import feature

6. **More Tools** — Expand capabilities
   - app_launch (start apps)
   - notification_read
   - camera_capture
   - file_read/file_write
   - See docs/TOOLS.md for full wishlist

---

## 🚀 How to Use It

### Push to GitHub

```bash
cd /root/.openclaw/workspace/android-agent

# Create a new repo on GitHub (via web UI)
# Then:
git remote add origin https://github.com/YOUR_USERNAME/android-agent.git
git push -u origin main

# GitHub Actions will automatically build the APK
# Check the "Actions" tab to see progress
```

### Build Locally (Optional)

Requires Android Studio + Android SDK:

```bash
# Open in Android Studio
# Or build via command line (if you have JDK 17):
./gradlew assembleDebug

# APK will be in:
# app/build/outputs/apk/debug/app-debug.apk
```

### Test on Device

1. Enable Developer Options on Android device
2. Enable USB Debugging
3. Connect device via USB
4. Install APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
5. Open app on device
6. Enable Accessibility Service (Settings → Accessibility → Android Agent → ON)
7. Test basic commands (currently placeholder responses)

---

## 📚 Key Files to Know

| File | Purpose |
|------|---------|
| `AgentCore.kt` | Main agent loop — start here for LLM integration |
| `ChatScreen.kt` | UI — handles user input and displays messages |
| `AgentAccessibilityService.kt` | Device control via accessibility API |
| `tools/*.kt` | Individual tool implementations |
| `build.yml` | GitHub Actions CI/CD workflow |
| `PROJECT_STATUS.md` | Detailed status & roadmap |

---

## 💡 Tips

1. **LLM Integration**: Look at OpenClaw's HTTP client code for inspiration. You'll need:
   - Retrofit + OkHttp for HTTP
   - JSON parsing for tool calls
   - Function schema generation from Tool definitions

2. **Testing Accessibility**: 
   - Use `adb logcat | grep AndroidAgent` to see logs
   - Check if service is running: `adb shell dumpsys accessibility`

3. **Adding Tools**: 
   - Create new class in `tools/` implementing `Tool` interface
   - Register in `AgentCore.init()`
   - Update docs/TOOLS.md

4. **Debugging**: 
   - Android Studio has great debugging for services
   - Set breakpoints in `AgentAccessibilityService`
   - Use Logcat filters

---

## 🎯 Recommended First Task

**Implement LLM Integration (Ollama/OpenAI-compatible)**

1. Add dependencies to `app/build.gradle.kts`:
   ```kotlin
   implementation("com.squareup.retrofit2:retrofit:2.9.0")
   implementation("com.squareup.retrofit2:converter-gson:2.9.0")
   implementation("com.squareup.okhttp3:okhttp:4.12.0")
   ```

2. Create `llm/OllamaClient.kt`:
   ```kotlin
   interface LLMApi {
       @POST("chat/completions")
       suspend fun chat(@Body request: ChatRequest): ChatResponse
   }
   ```

3. Update `AgentCore.processWithLLM()` to:
   - Build messages array (history + user input)
   - Add tools schema
   - Call LLM API
   - Parse response for tool calls
   - Execute tools if needed
   - Loop until final text response

4. Create SettingsScreen to configure endpoint URL

5. Test with local Ollama server

---

## 📖 Documentation

All the docs you need:
- **README.md** — Overview, quick start, features
- **docs/ARCHITECTURE.md** — How everything fits together
- **docs/TOOLS.md** — Tool reference and examples
- **docs/SETUP.md** — Installation and configuration
- **docs/CONTRIBUTING.md** — How to add features
- **PROJECT_STATUS.md** — Detailed status and roadmap

---

## ✨ What Makes This Cool

- **Standalone** — No PC required, runs entirely on Android
- **Non-Root First** — Works on any Android 8+ device
- **Root Enhanced** — Advanced features if you have root
- **Modern Stack** — Kotlin, Jetpack Compose, Material 3
- **Extensible** — Easy to add new tools
- **Well-Documented** — Comprehensive guides for users and devs
- **CI/CD Ready** — GitHub Actions builds APKs automatically

---

## 🙏 Credits

Built by Thor ⚡ for Ash  
Inspired by OpenClaw  
MIT Licensed — do whatever you want with it!

---

**Questions?** Check the docs or ask in GitHub Issues once you push the repo.

**Good luck!** 🚀
