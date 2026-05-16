package com.bitblazer.tinyhands

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import android.util.Log
import androidx.core.app.NotificationCompat

class OverlayService : Service() {


    // ── Shared state (readable by TileService / MainActivity) ─────────────────
    companion object {
        private const val TAG = "TinyHands/OverlayService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "touch_blocker_channel"

        @Volatile var isRunning: Boolean = false
        @Volatile var isLocked: Boolean = false
    }


    // ── Fields ────────────────────────────────────────────────────────────────

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: BlockingOverlayView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var notificationManager: NotificationManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var viewAttached = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            // Re-attach view on rotation / display change
            reattachOverlay()
        }
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == Actions.STOP_SERVICE) {
            Log.i(TAG, "Stop action received — stopping self")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!viewAttached) {
            setupOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — cleaning up overlay")
        isRunning = false
        isLocked = false

        if (viewAttached) {
            try {
                windowManager.removeView(overlayView)
                Log.d(TAG, "Overlay view removed")
            } catch (e: Exception) {
                Log.w(TAG, "removeView failed: ${e.message}")
            }
            viewAttached = false
        }

        (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .unregisterDisplayListener(displayListener)

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }

        broadcastState()
        super.onDestroy()
    }

    // ── Overlay setup ─────────────────────────────────────────────────────────

    private fun setupOverlay() {
        Log.i(TAG, "setupOverlay — starting in LOCKED mode")

        // No FLAG_NOT_TOUCHABLE — overlay must receive touches so triple-tap detection works.
        // Touch blocking is handled by BlockingOverlayView.onTouchEvent returning true.
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Triple-tap → deactivate (stop service entirely)
        overlayView = BlockingOverlayView(this).apply {
            onToggleCallback = ::deactivate
        }

        try {
            windowManager.addView(overlayView, layoutParams)
            viewAttached = true
            Log.d(TAG, "Overlay view attached to WindowManager")
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "BadTokenException attaching overlay: ${e.message}")
            stopSelf()
            return
        }

        (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, null)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "TinyHands:OverlayWakeLock"
        ).also { it.acquire(10 * 60 * 60 * 1000L) }
        Log.d(TAG, "WakeLock acquired")

        isRunning = true
        isLocked = true   // always locked while overlay is running
        startForeground(NOTIFICATION_ID, buildNotification())
        broadcastState()
        Log.i(TAG, "Overlay active and locked")
    }

    // ── Deactivation (triggered by triple-tap) ────────────────────────────────

    private fun deactivate() {
        Log.i(TAG, "deactivate() — triple-tap received, stopping service")
        stopSelf()
    }

    // ── Display / rotation handling ───────────────────────────────────────────

    private fun reattachOverlay() {
        if (!viewAttached) return
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) { /* ignore */ }
        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: WindowManager.BadTokenException) {
            viewAttached = false
            stopSelf()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Touch Blocker overlay service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = Actions.STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                if (isLocked) getString(R.string.notif_text_locked)
                else getString(R.string.notif_text_unlocked)
            )
            .setSmallIcon(R.drawable.ic_lock)
            .setContentIntent(openAppIntent)
            .addAction(
                R.drawable.ic_unlock,
                getString(R.string.notif_action_disable),
                stopIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private fun broadcastState() {
        sendBroadcast(Intent(Actions.STATE_CHANGED).apply {
            putExtra(Extras.IS_RUNNING, isRunning)
            putExtra(Extras.IS_LOCKED, isLocked)
            setPackage(packageName)
        })
    }
}
