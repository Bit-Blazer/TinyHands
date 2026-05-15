package com.bitblazer.tinyhands

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TinyHandsTileService : TileService() {

    private var stateReceiver: BroadcastReceiver? = null

    // ── Tile lifecycle ────────────────────────────────────────────────────────

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        registerStateReceiver()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterStateReceiver()
    }

    // ── Click handling ────────────────────────────────────────────────────────

    override fun onClick() {
        super.onClick()

        if (!isOverlayPermissionGranted(this)) {
            // Send user to overlay permission settings
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        updateTile()
    }

    // ── Tile state update ─────────────────────────────────────────────────────

    private fun updateTile() {
        val tile = qsTile ?: return
        val permOk = isOverlayPermissionGranted(this)
        val running = OverlayService.isRunning
        val locked = OverlayService.isLocked

        tile.state = when {
            !permOk  -> Tile.STATE_UNAVAILABLE
            !running -> Tile.STATE_INACTIVE
            else     -> Tile.STATE_ACTIVE
        }

        val subtitle = when {
            !permOk  -> getString(R.string.tile_subtitle_no_permission)
            !running -> getString(R.string.tile_subtitle_inactive)
            locked   -> getString(R.string.tile_subtitle_locked)
            else     -> getString(R.string.tile_subtitle_unlocked)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }

        tile.updateTile()
    }

    // ── Broadcast receiver ────────────────────────────────────────────────────

    private fun registerStateReceiver() {
        val filter = IntentFilter(Actions.STATE_CHANGED)
        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateTile()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            stateReceiver!!,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterStateReceiver() {
        stateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // Already unregistered
            }
        }
        stateReceiver = null
    }
}
