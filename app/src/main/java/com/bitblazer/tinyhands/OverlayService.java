package com.bitblazer.tinyhands;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.WindowManager;

public class OverlayService extends Service {

    public static final int NOTIFICATION_ID = 1001;
    public static final String CHANNEL_ID = "touch_blocker_channel";
    private static final long AUTH_TIMEOUT_MS = 15000L;

    public static volatile boolean isRunning = false;
    public static volatile boolean isLocked = false;

    private WindowManager windowManager;
    private BlockingOverlayView overlayView;
    private WindowManager.LayoutParams layoutParams;
    private NotificationManager notificationManager;

    private PowerManager.WakeLock wakeLock;
    private boolean viewAttached = false;
    private boolean isAuthInProgress = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable authTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handleAuthResult(false);
        }
    };

    private BroadcastReceiver authReceiver;

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (viewAttached && overlayView != null && overlayView.isAttachedToWindow()) {
                try {
                    windowManager.updateViewLayout(overlayView, layoutParams);
                } catch (Exception ignored) {}
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        registerAuthReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (Utils.Actions.STOP_SERVICE.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        } else if (Utils.Actions.START_BIOMETRIC_AUTH.equals(action)) {
            startBiometricAuth();
            return START_STICKY;
        } else if (Utils.Actions.RESTORE_OVERLAY.equals(action)) {
            restoreOverlay();
            return START_STICKY;
        }

        if (!viewAttached && !isAuthInProgress) {
            setupOverlay();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        isLocked = false;
        mainHandler.removeCallbacks(authTimeoutRunnable);
        unregisterAuthReceiver();

        if (viewAttached) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            viewAttached = false;
        }

        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (dm != null) {
            dm.unregisterDisplayListener(displayListener);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {}

        broadcastState();
        super.onDestroy();
    }

    private void setupOverlay() {
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;

        overlayView = new BlockingOverlayView(this);
        overlayView.setOnBiometricRequestListener(new BlockingOverlayView.OnBiometricRequestListener() {
            @Override
            public void onBiometricRequested() {
                startBiometricAuth();
            }
        });

        try {
            windowManager.addView(overlayView, layoutParams);
            viewAttached = true;
        } catch (Exception e) {
            stopSelf();
            return;
        }

        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (dm != null) {
            dm.registerDisplayListener(displayListener, null);
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            @SuppressWarnings("deprecation")
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "TinyHands:OverlayWakeLock"
            );
            wakeLock = wl;
            wl.acquire(10 * 60 * 60 * 1000L);
        }

        isRunning = true;
        isLocked = true;
        startForeground(NOTIFICATION_ID, buildNotification());
        broadcastState();
    }

    private void startBiometricAuth() {
        if (isAuthInProgress) {
            return;
        }
        isAuthInProgress = true;

        if (overlayView != null) {
            overlayView.resetState();
        }

        if (viewAttached) {
            try {
                windowManager.removeView(overlayView);
                viewAttached = false;
            } catch (Exception ignored) {}
        }

        Intent authIntent = new Intent(this, BiometricAuthActivity.class);
        authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        startActivity(authIntent);

        mainHandler.removeCallbacks(authTimeoutRunnable);
        mainHandler.postDelayed(authTimeoutRunnable, AUTH_TIMEOUT_MS);
    }

    private void handleAuthResult(boolean success) {
        mainHandler.removeCallbacks(authTimeoutRunnable);
        isAuthInProgress = false;

        if (success) {
            stopSelf();
        } else {
            restoreOverlay();
        }
    }

    private void restoreOverlay() {
        mainHandler.removeCallbacks(authTimeoutRunnable);
        isAuthInProgress = false;

        if (!isRunning) {
            return;
        }

        if (overlayView != null) {
            overlayView.resetState();
        }

        if (!viewAttached) {
            try {
                windowManager.addView(overlayView, layoutParams);
                viewAttached = true;
            } catch (Exception ignored) {}
        }
    }

    private void registerAuthReceiver() {
        if (authReceiver != null) {
            return;
        }
        authReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                boolean authSuccess = intent != null && intent.getBooleanExtra(Utils.Extras.AUTH_SUCCESS, false);
                if (Utils.Actions.BIOMETRIC_AUTH_RESULT.equals(action)) {
                    handleAuthResult(authSuccess);
                } else if (Utils.Actions.RESTORE_OVERLAY.equals(action)) {
                    restoreOverlay();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.Actions.BIOMETRIC_AUTH_RESULT);
        filter.addAction(Utils.Actions.RESTORE_OVERLAY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(authReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(authReceiver, filter);
        }
    }

    private void unregisterAuthReceiver() {
        if (authReceiver != null) {
            try {
                unregisterReceiver(authReceiver);
            } catch (Exception ignored) {}
            authReceiver = null;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Touch Blocker overlay service");
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent stopServiceIntent = new Intent(this, OverlayService.class);
        stopServiceIntent.setAction(Utils.Actions.STOP_SERVICE);
        PendingIntent stopIntent = PendingIntent.getService(
                this, 0,
                stopServiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent openAppIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(isLocked ? getString(R.string.notif_text_locked) : getString(R.string.notif_text_unlocked))
                .setSmallIcon(R.drawable.ic_lock)
                .setContentIntent(openAppIntent)
                .addAction(
                        new Notification.Action.Builder(
                                Icon.createWithResource(this, R.drawable.ic_unlock),
                                getString(R.string.notif_action_disable),
                                stopIntent
                        ).build()
                )
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private void broadcastState() {
        Intent intent = new Intent(Utils.Actions.STATE_CHANGED);
        intent.putExtra(Utils.Extras.IS_RUNNING, isRunning);
        intent.putExtra(Utils.Extras.IS_LOCKED, isLocked);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
