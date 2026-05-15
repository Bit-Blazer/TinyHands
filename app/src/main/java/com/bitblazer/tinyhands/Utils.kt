package com.bitblazer.tinyhands

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings

// ── Broadcast action constants ────────────────────────────────────────────────
object Actions {
    const val STATE_CHANGED = "com.bitblazer.tinyhands.STATE_CHANGED"
    const val STOP_SERVICE  = "com.bitblazer.tinyhands.ACTION_STOP"
}

// ── Broadcast extra keys ──────────────────────────────────────────────────────
object Extras {
    const val IS_RUNNING = "isRunning"
    const val IS_LOCKED  = "isLocked"
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns true if the given service class is currently running. */
@Suppress("DEPRECATION")
fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == serviceClass.name }
}

/** Returns true if SYSTEM_ALERT_WINDOW permission has been granted. */
fun isOverlayPermissionGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

/** Converts dp to pixels. */
fun dpToPx(context: Context, dp: Float): Int =
    (dp * context.resources.displayMetrics.density + 0.5f).toInt()
