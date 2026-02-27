# Contributing to Android Agent

Thanks for your interest! Here's how you can help.

## Areas Needing Help

1. **Tools** — Add new capabilities (camera, contacts, calendar, etc.)
2. **LLM Integration** — Better function calling, multi-turn conversations
3. **UI/UX** — Settings screen, tool execution logs, dark mode
4. **Testing** — Unit tests, integration tests, device compatibility
5. **Documentation** — Tutorials, examples, troubleshooting guides
6. **Performance** — Optimize screen parsing, reduce latency
7. **Security** — Audit tool execution, add safety filters

## Getting Started

1. **Fork the repo**
   ```bash
   git clone https://github.com/YOUR_USERNAME/android-agent.git
   cd android-agent
   ```

2. **Open in Android Studio**
   - File → Open → Select `android-agent` folder
   - Wait for Gradle sync

3. **Run on device/emulator**
   - Connect Android device or start emulator
   - Run → Run 'app'

4. **Make changes**
   - Create a feature branch: `git checkout -b feature/my-feature`
   - Write code
   - Test thoroughly

5. **Submit PR**
   - Push to your fork
   - Open Pull Request on GitHub
   - Describe what you changed and why

## Code Style

- **Kotlin**: Follow [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- **Formatting**: Use Android Studio's auto-format (Ctrl+Alt+L)
- **Naming**: Descriptive variable names, avoid abbreviations
- **Comments**: Explain "why", not "what"

## Adding a New Tool

1. Create file in `app/src/main/kotlin/ai/openclaw/androidagent/tools/`
2. Implement `Tool` interface
3. Register in `AgentCore.init()`
4. Add documentation to `docs/TOOLS.md`
5. Write tests

**Example:**
```kotlin
class BatteryTool : Tool {
    override val name = "get_battery"
    override val description = "Get current battery level"
    override val parameters = emptyMap<String, ToolParameter>()
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return ToolResult(true, data = "$level%")
    }
}
```

## Testing

- **Run tests**: `./gradlew test`
- **UI tests**: `./gradlew connectedAndroidTest`
- **Lint**: `./gradlew lint`

## Pull Request Checklist

- [ ] Code builds without errors
- [ ] No lint warnings
- [ ] Tests pass
- [ ] Documentation updated (if adding features)
- [ ] Commit messages are clear
- [ ] PR description explains the change

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add camera capture tool
fix: handle null accessibility service
docs: update TOOLS.md with new examples
refactor: simplify tool execution logic
test: add unit tests for TapTool
```

## Questions?

- Open an issue
- Join the discussion in Issues/Discussions
- Tag @maintainers in your PR

## License

By contributing, you agree your code will be licensed under MIT License.
