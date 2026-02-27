# Android Agent 🤖

An AI agent runtime for Android that controls your device through accessibility services or root access. Think OpenClaw, but for Android.

[![Build Status](https://github.com/YOUR_USERNAME/android-agent/workflows/Android%20CI/badge.svg)](https://github.com/YOUR_USERNAME/android-agent/actions)

## Features

- ✅ **Accessibility-based automation** (no root required)
- ✅ **Root enhancement** (optional, for advanced control)
- ✅ **Tool system** (tap, swipe, type, screenshot, shell commands)
- ✅ **LLM integration** (local or remote)
- ✅ **Jetpack Compose UI** (modern Android UI)
- ✅ **Standalone app** (no PC required)

## Architecture

```
┌─────────────────────────────────────┐
│   Android App (Kotlin)              │
├─────────────────────────────────────┤
│  UI Layer (Jetpack Compose)         │
│   - Chat interface                  │
│   - Settings & permissions          │
├─────────────────────────────────────┤
│  Agent Core                         │
│   - Session management              │
│   - Tool execution engine           │
│   - Memory system                   │
│   - LLM integration                 │
├─────────────────────────────────────┤
│  Control Layer                      │
│   ├─ Accessibility Service          │
│   │   - View hierarchy parsing      │
│   │   - Gesture injection           │
│   ├─ Root Module (optional)         │
│   │   - Shell command execution     │
│   └─ Device APIs                    │
│       - Sensors, camera, files      │
├─────────────────────────────────────┤
│  Tool System                        │
│   - tap, swipe, type_text           │
│   - screenshot (hierarchy)          │
│   - shell (with optional root)      │
└─────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Android 8.0 (API 26) or higher
- Optional: Rooted device for advanced features

### Installation

#### Option 1: Download APK from Releases
1. Go to [Releases](https://github.com/YOUR_USERNAME/android-agent/releases)
2. Download the latest `app-debug.apk`
3. Install on your device
4. Enable Accessibility Service in Settings

#### Option 2: Build from Source
```bash
git clone https://github.com/YOUR_USERNAME/android-agent.git
cd android-agent
./gradlew assembleDebug
# APK will be in app/build/outputs/apk/debug/app-debug.apk
```

### Setup

1. **Enable Accessibility Service**
   - Open the app
   - Tap "Enable Accessibility"
   - Navigate to Settings → Accessibility → Android Agent
   - Toggle ON

2. **Configure LLM Endpoint** (optional)
   - Open app settings
   - Enter your LLM API endpoint (Ollama, OpenAI-compatible, etc.)
   - Example: `http://192.168.1.100:11434/v1/chat/completions`

3. **Start using!**
   - Type commands in the chat
   - The agent will use tools to control your device

## Built-in Tools

| Tool | Description | Parameters |
|------|-------------|------------|
| `tap` | Tap at coordinates | `x`, `y`, `duration` |
| `swipe` | Swipe gesture | `startX`, `startY`, `endX`, `endY`, `duration` |
| `type_text` | Type into focused field | `text` |
| `screenshot` | Get UI hierarchy as JSON | - |
| `shell` | Execute shell command | `command`, `useRoot` |
| `find_element` | Find UI element by text/ID | `text`, `description`, `resourceId` |
| `launch_app` | Launch app by name or package | `appName`, `packageName` |
| `navigate` | System navigation (back/home) | `action` |
| `get_current_app` | Get active app info | - |

## LLM Integration

### Local (On-Device)

Run Ollama on your Android device via Termux:
```bash
# In Termux
pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu
# Then install Ollama in Ubuntu
curl -fsSL https://ollama.com/install.sh | sh
ollama serve
```

Point the app to: `http://localhost:11434/v1/chat/completions`

### Remote (Desktop/Server)

Run Ollama, LM Studio, or any OpenAI-compatible server on your network:
```bash
# On your desktop
ollama serve
```

Point the app to: `http://YOUR_COMPUTER_IP:11434/v1/chat/completions`

### Cloud (OpenAI, Anthropic, etc.)

Any OpenAI-compatible endpoint works:
- OpenRouter: `https://openrouter.ai/api/v1/chat/completions`
- OpenAI: `https://api.openai.com/v1/chat/completions`

## Permissions

| Permission | Purpose | Required |
|------------|---------|----------|
| `INTERNET` | LLM API calls | Yes |
| `ACCESSIBILITY_SERVICE` | Device control | Yes |
| `FOREGROUND_SERVICE` | Background operation | Yes |
| `ACCESS_SUPERUSER` | Root commands | No |

## Development

### Build

```bash
./gradlew assembleDebug       # Debug APK
./gradlew assembleRelease     # Release APK (requires signing)
./gradlew test                # Run tests
```

### GitHub Actions

The project includes a CI workflow that automatically:
- Builds APK on every push
- Uploads artifacts (30-day retention)
- Creates GitHub releases on tags

To create a release:
```bash
git tag v0.1.0
git push origin v0.1.0
# GitHub Actions will build and attach APK to the release
```

## Project Structure

```
android-agent/
├── .github/workflows/    # CI/CD workflows
├── app/
│   ├── src/main/
│   │   ├── kotlin/
│   │   │   ├── core/         # Agent runtime
│   │   │   ├── services/     # Accessibility & background
│   │   │   ├── tools/        # Tool implementations
│   │   │   ├── ui/           # Jetpack Compose UI
│   │   │   └── models/       # Data models
│   │   ├── res/              # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs/                 # Documentation
└── README.md
```

## Roadmap

### Phase 1: Core Runtime ✅
- [x] Basic Android app scaffold
- [x] Agent loop (prompt → tools → response)
- [x] Chat UI
- [x] GitHub Actions CI

### Phase 2: Accessibility Layer ✅
- [x] Accessibility Service implementation
- [x] Screen content extraction
- [x] Gesture injection (tap, swipe, type)
- [x] Element finder (by text, ID, coordinates)
- [x] Screenshot capture (hierarchy-based, pixel WIP)

### Phase 3: Root Enhancement
- [x] Root detection
- [x] Shell execution via `su`
- [ ] Direct input injection (`/dev/input/event*`)
- [ ] Advanced automation (install APKs, modify settings)

### Phase 4: Skills & Tools (In Progress)
- [x] App launcher & control
- [x] Navigation (back, home, recents)
- [x] Current app detection
- [ ] Browser automation
- [ ] Notification reader/responder
- [ ] Camera & sensor access
- [ ] File manager
- [ ] Contacts & calendar integration

### Phase 5: Memory & Context
- [ ] Persistent memory (Room DB)
- [ ] Session management
- [ ] Skill installation system
- [ ] User preferences

### Phase 6: Advanced Features
- [ ] Multi-agent orchestration
- [ ] Voice input/output
- [ ] OCR for visual understanding
- [ ] Image recognition (ML Kit)
- [ ] Task scheduling

## Security & Privacy

- **Local-first**: Works offline with local LLM
- **Transparent**: All tool calls logged and visible
- **Sandboxed**: Accessibility API is safer than raw root
- **Open source**: Audit the code yourself

## Contributing

Pull requests welcome! Areas needing help:
- Additional tools (calendar, contacts, camera)
- LLM integration improvements
- UI/UX enhancements
- Testing & documentation

## License

MIT License - see [LICENSE](LICENSE)

## Acknowledgments

- Inspired by [OpenClaw](https://openclaw.ai)
- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Uses [libsuperuser](https://github.com/Chainfire/libsuperuser) for root access

---

**Warning**: This is experimental software. Use at your own risk. The accessibility service has broad permissions — only install from trusted sources.
