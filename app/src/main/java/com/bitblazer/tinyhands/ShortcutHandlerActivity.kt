package com.bitblazer.tinyhands

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity

/**
 * Transparent, no-display activity launched by the long-press app shortcut.
 * Toggles the overlay service (start or stop) and immediately finishes —
 * the user never sees this activity at all.
 */
class ShortcutHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isOverlayPermissionGranted(this)) {
            // No permission — open the main app so the user can grant it
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } else if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            startForegroundService(Intent(this, OverlayService::class.java))
        }

        finish()
    }
}
