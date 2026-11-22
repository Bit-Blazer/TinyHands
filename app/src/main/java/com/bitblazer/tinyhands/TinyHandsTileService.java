package com.bitblazer.tinyhands;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class TinyHandsTileService extends TileService {

    private BroadcastReceiver stateReceiver;

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        registerStateReceiver();
        updateTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        unregisterStateReceiver();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        super.onClick();

        if (!Utils.isOverlayPermissionGranted(this)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                );
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
            return;
        }

        Intent intent = new Intent(this, ShortcutHandlerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean permOk = Utils.isOverlayPermissionGranted(this);
        boolean running = OverlayService.isRunning;
        boolean locked = OverlayService.isLocked;

        if (!permOk) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else if (!running) {
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            tile.setState(Tile.STATE_ACTIVE);
        }

        if (running) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_lock));
        } else {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_unlock));
        }

        String subtitle;
        if (!permOk) {
            subtitle = getString(R.string.tile_subtitle_no_permission);
        } else if (!running) {
            subtitle = getString(R.string.tile_subtitle_inactive);
        } else if (locked) {
            subtitle = getString(R.string.tile_subtitle_locked);
        } else {
            subtitle = getString(R.string.tile_subtitle_unlocked);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(subtitle);
        }

        tile.updateTile();
    }

    private void registerStateReceiver() {
        IntentFilter filter = new IntentFilter(Utils.Actions.STATE_CHANGED);
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateTile();
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
    }

    private void unregisterStateReceiver() {
        if (stateReceiver != null) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            stateReceiver = null;
        }
    }
}
