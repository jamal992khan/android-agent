# Setup Guide

## For End Users

### 1. Install the APK

Download from [Releases](https://github.com/YOUR_USERNAME/android-agent/releases) or build from source.

```bash
# If building from source
git clone https://github.com/YOUR_USERNAME/android-agent.git
cd android-agent
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Enable Accessibility Service

This is **required** for the agent to control your device.

1. Open the Android Agent app
2. Tap "Enable Accessibility" (or go to Settings → Accessibility)
3. Find "Android Agent" in the list
4. Toggle it ON
5. Confirm the warning dialog

**What this gives the app:**
- Read screen content
- Perform taps, swipes, scrolls
- Type text into fields
- Monitor app changes

### 3. Configure LLM (Optional)

The app needs an LLM to process your requests. You have options:

#### Option A: Local LLM (Privacy-first, works offline)

**Using Termux:**
```bash
# Install Termux from F-Droid
# Then in Termux:
pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu

# Inside Ubuntu:
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
ollama pull llama3.2
```

**In Android Agent app:**
- Endpoint: `http://localhost:11434/v1/chat/completions`
- Model: `llama3.2`

#### Option B: Desktop LLM Server

Run on your PC and connect via local network:

```bash
# On your desktop/laptop
ollama serve
# Or: lm studio, text-generation-webui, etc.
```

**Find your PC's IP:**
```bash
# Linux/Mac
ifconfig | grep "inet "
# Windows
ipconfig
```

**In Android Agent app:**
- Endpoint: `http://192.168.1.XXX:11434/v1/chat/completions`
- Model: `llama3.2`

#### Option C: Cloud API

Use OpenRouter, OpenAI, or any OpenAI-compatible endpoint:

**In Android Agent app:**
- Endpoint: `https://openrouter.ai/api/v1/chat/completions`
- API Key: `sk-or-v1-...`
- Model: `meta-llama/llama-3.2-3b-instruct:free`

### 4. Test It

1. Open the app
2. Type: "Tap at coordinates 500, 1000"
3. The agent should perform the tap

Try:
- "Swipe down from top of screen"
- "Type 'Hello' into the input field"
- "Show me the screen hierarchy"

---

## For Developers

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- A physical device or emulator (API 26+)

### First-Time Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/YOUR_USERNAME/android-agent.git
   cd android-agent
   ```

2. **Open in Android Studio**
   - File → Open → Select `android-agent`
   - Wait for Gradle sync

3. **Create local.properties** (if needed)
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

4. **Build**
   ```bash
   ./gradlew assembleDebug
   ```

5. **Run**
   - Connect device or start emulator
   - Click Run button (green triangle)
   - Or: `./gradlew installDebug`

### Debugging Accessibility Service

Accessibility services can be tricky to debug. Here's how:

1. **Enable USB Debugging** on your device
2. **Enable Accessibility Service** for Android Agent
3. **View logs:**
   ```bash
   adb logcat | grep AndroidAgent
   ```

4. **Check if service is running:**
   ```bash
   adb shell dumpsys accessibility | grep -A 10 "AndroidAgent"
   ```

5. **Force stop and restart:**
   ```bash
   adb shell am force-stop ai.openclaw.androidagent
   adb shell am start -n ai.openclaw.androidagent/.MainActivity
   ```

### Testing Root Features

1. **Use a rooted emulator** (AVD with Google APIs, not Play Store)
2. **Or root a physical device** (Magisk recommended)
3. **Check root access:**
   ```bash
   adb shell
   su
   whoami  # Should show "root"
   ```

4. **Test shell tool:**
   ```kotlin
   shellTool.execute(mapOf(
       "command" to "whoami",
       "useRoot" to true
   ))
   ```

### Common Issues

**Gradle sync fails:**
- Update Android Studio
- File → Invalidate Caches / Restart
- Check internet connection (downloads dependencies)

**App crashes on launch:**
- Check logcat for stack trace
- Verify minSdk matches device Android version

**Accessibility service not working:**
- Make sure it's enabled in Settings
- Check `AgentAccessibilityService.instance` is not null
- Verify permissions in AndroidManifest.xml

**Build fails in GitHub Actions:**
- Check Java version (must be 17)
- Verify gradlew is executable (`chmod +x gradlew`)
- Check for hardcoded paths in build files

---

## GitHub Actions CI/CD

The repo includes a workflow that:
- ✅ Builds APK on every push
- ✅ Uploads artifact (available for 30 days)
- ✅ Creates releases when you push a tag

**To create a release:**

```bash
# Update version in app/build.gradle.kts
# Commit changes
git add .
git commit -m "chore: bump version to 0.2.0"

# Tag the release
git tag v0.2.0
git push origin main
git push origin v0.2.0

# GitHub Actions will build and attach APK to the release
```

**Download build artifacts:**
1. Go to Actions tab on GitHub
2. Click on a workflow run
3. Scroll to "Artifacts"
4. Download `android-agent-debug`

---

## Next Steps

Once setup is complete:
- Read [ARCHITECTURE.md](ARCHITECTURE.md) to understand how it works
- Read [TOOLS.md](TOOLS.md) to see available tools
- Read [CONTRIBUTING.md](CONTRIBUTING.md) to add features
- Open an issue if you hit problems!
