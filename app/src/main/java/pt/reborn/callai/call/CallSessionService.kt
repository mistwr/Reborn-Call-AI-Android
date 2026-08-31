package pt.reborn.callai.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import pt.reborn.callai.R
import pt.reborn.callai.agent.RebornVoicePipeline
import pt.reborn.callai.capture.ShellCaptureBridge

class CallSessionService : Service() {

    private lateinit var capture: ShellCaptureBridge
    private val pipeline = RebornVoicePipeline()

    override fun onCreate() {
        super.onCreate()
        capture = ShellCaptureBridge(this)
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("REBORN Call AI")
                .setContentText("Call audio bridge ready")
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setOngoing(true)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!capture.isRunning) {
            capture.start(pipeline::acceptRemoteAudio)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        capture.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "REBORN Call AI",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "reborn_call_ai"
        private const val NOTIFICATION_ID = 1001
    }
}
