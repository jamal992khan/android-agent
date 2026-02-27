# Tools Reference

## Overview

Tools are the agent's hands — functions it can call to interact with your device. Each tool has a name, description, parameters, and execution logic.

## Built-in Tools

### tap

**Description:** Tap at specific screen coordinates

**Parameters:**
- `x` (integer, required): X coordinate in pixels
- `y` (integer, required): Y coordinate in pixels
- `duration` (integer, optional): Tap duration in milliseconds (default: 100)

**Example:**
```json
{
  "name": "tap",
  "arguments": {
    "x": 500,
    "y": 1000,
    "duration": 150
  }
}
```

**Requires:** Accessibility Service enabled

**Notes:**
- Coordinates are in screen pixels (top-left is 0,0)
- Longer duration simulates "long press"
- May fail if target app blocks accessibility

---

### swipe

**Description:** Swipe from one point to another

**Parameters:**
- `startX` (integer, required): Start X coordinate
- `startY` (integer, required): Start Y coordinate
- `endX` (integer, required): End X coordinate
- `endY` (integer, required): End Y coordinate
- `duration` (integer, optional): Swipe duration in milliseconds (default: 300)

**Example:**
```json
{
  "name": "swipe",
  "arguments": {
    "startX": 500,
    "startY": 1500,
    "endX": 500,
    "endY": 500,
    "duration": 400
  }
}
```

**Requires:** Accessibility Service enabled

**Common Use Cases:**
- Scroll down: `startY` > `endY`
- Scroll up: `startY` < `endY`
- Horizontal scroll: Change `startX`/`endX`

---

### type_text

**Description:** Type text into the currently focused input field

**Parameters:**
- `text` (string, required): Text to type

**Example:**
```json
{
  "name": "type_text",
  "arguments": {
    "text": "Hello, world!"
  }
}
```

**Requires:** Accessibility Service enabled

**Notes:**
- Input field must be focused first (user taps it, or agent taps it via `tap` tool)
- Replaces existing text in the field
- Works with EditText, search bars, etc.

---

### screenshot

**Description:** Get current screen hierarchy as JSON

**Parameters:** None

**Example:**
```json
{
  "name": "screenshot",
  "arguments": {}
}
```

**Returns:**
```json
{
  "className": "android.widget.FrameLayout",
  "text": "",
  "contentDescription": "",
  "viewIdResourceName": "",
  "clickable": false,
  "focusable": false,
  "enabled": true,
  "bounds": {
    "left": 0,
    "top": 0,
    "right": 1080,
    "bottom": 2400
  },
  "children": [
    {
      "className": "android.widget.TextView",
      "text": "Welcome",
      "clickable": true,
      "bounds": {...}
    }
  ]
}
```

**Requires:** Accessibility Service enabled

**Notes:**
- Returns view hierarchy, NOT a pixel screenshot
- Use this to understand what's on screen
- Parse `bounds` to find tap coordinates
- Look for `clickable: true` elements to interact with

---

### shell

**Description:** Execute shell command (requires root if `useRoot=true`)

**Parameters:**
- `command` (string, required): Shell command to execute
- `useRoot` (boolean, optional): Execute as root (default: false)

**Example (non-root):**
```json
{
  "name": "shell",
  "arguments": {
    "command": "ls -la /sdcard/"
  }
}
```

**Example (root):**
```json
{
  "name": "shell",
  "arguments": {
    "command": "pm list packages",
    "useRoot": true
  }
}
```

**Requires:** 
- libsuperuser library
- Root access if `useRoot=true`

**Security Warning:** 
This tool can execute ANY command. Be careful with:
- `rm -rf` (deletion)
- `pm uninstall` (uninstall apps)
- `reboot` (restart device)

**Common Commands:**
```bash
# List installed apps
pm list packages

# Take actual screenshot (requires root)
screencap -p /sdcard/screenshot.png

# Simulate input (alternative to tap)
input tap 500 1000
input swipe 500 1500 500 500

# Get device info
getprop ro.build.version.release  # Android version
dumpsys battery                    # Battery status
```

---

## Upcoming Tools

### browser_open

**Description:** Open URL in default browser

**Parameters:**
- `url` (string): URL to open

---

### app_launch

**Description:** Launch an app by package name

**Parameters:**
- `package` (string): App package name (e.g., "com.android.chrome")

---

### notification_send

**Description:** Send a notification

**Parameters:**
- `title` (string): Notification title
- `message` (string): Notification body

---

### camera_capture

**Description:** Take a photo with camera

**Parameters:**
- `camera` (string): "front" or "back"
- `savePath` (string): Where to save image

---

### file_read

**Description:** Read file contents

**Parameters:**
- `path` (string): File path

---

### file_write

**Description:** Write content to file

**Parameters:**
- `path` (string): File path
- `content` (string): Content to write

---

### contacts_get

**Description:** Get contact info by name

**Parameters:**
- `name` (string): Contact name

---

### sms_send

**Description:** Send SMS (requires permission)

**Parameters:**
- `to` (string): Phone number
- `message` (string): SMS body

---

## Creating Custom Tools

### 1. Implement Tool Interface

```kotlin
package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult

class MyCustomTool : Tool {
    override val name = "my_tool"
    override val description = "Does something useful"
    override val parameters = mapOf(
        "param1" to ToolParameter("string", "Description", required = true)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val param1 = params["param1"] as? String
            ?: return ToolResult(false, error = "Invalid param1")
        
        // Your logic here
        
        return ToolResult(true, data = "Success!")
    }
}
```

### 2. Register in AgentCore

```kotlin
// In AgentCore.init()
registerTool(MyCustomTool())
```

### 3. Test

The LLM will now see your tool in the function schema and can call it.

---

## Tool Best Practices

1. **Validate inputs** — Always check parameter types and ranges
2. **Fail gracefully** — Return `ToolResult(false, error="...")` instead of throwing
3. **Be descriptive** — Good descriptions help the LLM use tools correctly
4. **Keep it simple** — One tool = one action
5. **Handle permissions** — Check if required permissions are granted
6. **Timeout long operations** — Don't block forever
7. **Log actions** — Help debugging and audit trail

## Tool Limitations

- **Accessibility Service**
  - Can't interact with system UI (notifications shade, quick settings)
  - Some apps block accessibility (banking apps, etc.)
  - Performance varies by target app

- **Root Commands**
  - Device must be rooted
  - Can break SafetyNet (Google Pay, banking apps)
  - Security risk if misused

- **Android Version**
  - Some APIs only available on newer Android
  - Gesture dispatch requires API 24+ (Android 7.0)
