package com.openeq.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.openeq.app.R

class EqAudioService : Service() {
    private val engine = EqEngine()

    override fun onCreate() {
        super.onCreate()
        val chId = "openeq_eq"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(chId, "OpenEQ", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = NotificationCompat.Builder(this, chId)
            .setContentTitle("OpenEQ active")
            .setContentText("Audio effects engine running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, n)
        engine.attach(0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
