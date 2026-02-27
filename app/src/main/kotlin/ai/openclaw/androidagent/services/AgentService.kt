package ai.openclaw.androidagent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Background service that keeps the agent runtime alive
 */
class AgentService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Android Agent running in background"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Android Agent")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "agent_service_channel"
    }
}
