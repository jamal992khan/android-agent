package ai.openclaw.androidagent.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Accessibility service that provides device control capabilities
 */
class AgentAccessibilityService : AccessibilityService() {
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Handle accessibility events if needed
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Screen changed
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content updated
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    /**
     * Perform tap gesture at coordinates
     */
    suspend fun performTap(x: Int, y: Int, duration: Long = 100): Boolean = 
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    continuation.resume(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription) {
                    continuation.resume(false)
                }
            }, null)
        }
    
    /**
     * Perform swipe gesture
     */
    suspend fun performSwipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long = 300
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                continuation.resume(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription) {
                continuation.resume(false)
            }
        }, null)
    }
    
    /**
     * Get screen hierarchy as JSON
     */
    fun getScreenHierarchy(): JSONObject {
        val rootNode = rootInActiveWindow ?: return JSONObject().apply {
            put("error", "No active window")
        }
        
        return parseNodeTree(rootNode)
    }
    
    private fun parseNodeTree(node: AccessibilityNodeInfo): JSONObject {
        return JSONObject().apply {
            put("className", node.className?.toString() ?: "")
            put("text", node.text?.toString() ?: "")
            put("contentDescription", node.contentDescription?.toString() ?: "")
            put("viewIdResourceName", node.viewIdResourceName ?: "")
            put("clickable", node.isClickable)
            put("focusable", node.isFocusable)
            put("enabled", node.isEnabled)
            put("bounds", node.let {
                val rect = android.graphics.Rect()
                it.getBoundsInScreen(rect)
                JSONObject().apply {
                    put("left", rect.left)
                    put("top", rect.top)
                    put("right", rect.right)
                    put("bottom", rect.bottom)
                }
            })
            
            // Parse children
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    children.put(parseNodeTree(child))
                }
            }
            put("children", children)
        }
    }
    
    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
        
        fun isEnabled(): Boolean = instance != null
    }
}
