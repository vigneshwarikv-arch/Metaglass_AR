package com.metaglassar.planes.pilot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.metaglassar.planes.MainActivity
import com.metaglassar.planes.PlanesApp
import com.metaglassar.planes.R
import com.metaglassar.planes.core.Brand

/**
 * Keeps pilot mode (GPS, receiver, callouts) running with the screen off or another app in front.
 * A foreground service of type "location", started from the visible app. Its notification has a
 * one-tap "Turn off".
 */
class PilotService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      (application as PlanesApp).graph.pilot.stop()
      stopSelf()
      return START_NOT_STICKY
    }
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pilot mode", NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val off =
        PendingIntent.getService(
            this, 1, Intent(this, PilotService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
    val notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${Brand.NAME} pilot mode is on")
            .setContentText("Supplementary aid only · VFR · not a certified instrument")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Turn off", off)
            .build()
    try {
      ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    } catch (e: Exception) {
      // E.g. location permission missing: pilot mode still works while the screen is on.
      Log.w(TAG, "could not start foreground", e)
      stopSelf()
    }
    return START_NOT_STICKY
  }

  companion object {
    private const val TAG = "PilotService"
    private const val CHANNEL = "pilot"
    private const val NOTIFICATION_ID = 4000
    private const val ACTION_STOP = "com.metaglassar.planes.pilot.STOP"

    fun start(context: Context) {
      // A location-type foreground service needs the location permission first (Android 14+).
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
      try {
        ContextCompat.startForegroundService(context, Intent(context, PilotService::class.java))
      } catch (e: Exception) {
        Log.w(TAG, "could not start pilot service", e)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, PilotService::class.java))
    }
  }
}
