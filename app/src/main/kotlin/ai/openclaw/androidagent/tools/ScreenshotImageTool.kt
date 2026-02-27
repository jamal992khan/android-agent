package ai.openclaw.androidagent.tools

import ai.openclaw.androidagent.models.Tool
import ai.openclaw.androidagent.models.ToolParameter
import ai.openclaw.androidagent.models.ToolResult
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Captures actual pixel screenshot using MediaProjection API
 * Note: Requires user permission via MediaProjectionManager
 * For now, falls back to hierarchy-based screenshot
 */
class ScreenshotImageTool(private val context: Context) : Tool {
    override val name = "screenshot_image"
    override val description = "Take a pixel-perfect screenshot and save to file"
    override val parameters = mapOf(
        "filename" to ToolParameter("string", "Output filename (default: screenshot.png)", required = false),
        "quality" to ToolParameter("number", "JPEG quality 1-100 (default: 85)", required = false)
    )
    
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val filename = params["filename"] as? String ?: "screenshot_${System.currentTimeMillis()}.png"
        val quality = (params["quality"] as? Number)?.toInt() ?: 85
        
        // TODO: Implement MediaProjection screenshot
        // For now, return a helpful message about permissions needed
        
        return ToolResult(
            success = false,
            error = "Screenshot requires MediaProjection permission.\n\n" +
                    "Implementation needed:\n" +
                    "1. Request MediaProjection permission in MainActivity\n" +
                    "2. Store MediaProjection instance\n" +
                    "3. Use VirtualDisplay to capture screen\n\n" +
                    "For now, use 'screenshot' tool for UI hierarchy.\n" +
                    "Coming in next update!"
        )
        
        // Reference implementation (requires MediaProjection):
        /*
        val mediaProjection = getMediaProjection() ?: return ToolResult(
            false,
            error = "MediaProjection not available. User must grant screen capture permission."
        )
        
        val bitmap = captureScreen(mediaProjection)
        val file = File(context.getExternalFilesDir(null), filename)
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
        }
        
        bitmap.recycle()
        
        return ToolResult(true, data = "Screenshot saved to: ${file.absolutePath}")
        */
    }
    
    // Future implementation helpers:
    
    private suspend fun captureScreen(mediaProjection: MediaProjection): Bitmap = suspendCancellableCoroutine { continuation ->
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null, null
        )
        
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image, width, height)
                image.close()
                virtualDisplay.release()
                imageReader.close()
                continuation.resume(bitmap)
            }
        }, Handler(Looper.getMainLooper()))
    }
    
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }
}
