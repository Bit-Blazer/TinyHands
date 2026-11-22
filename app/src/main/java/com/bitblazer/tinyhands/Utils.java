package com.bitblazer.tinyhands;

import android.app.ActivityManager;
import android.content.Context;
import android.provider.Settings;
import java.util.List;

public final class Utils {
    private Utils() {}

    public static final class Actions {
        public static final String STATE_CHANGED         = "com.bitblazer.tinyhands.STATE_CHANGED";
        public static final String STOP_SERVICE          = "com.bitblazer.tinyhands.ACTION_STOP";
        public static final String START_BIOMETRIC_AUTH  = "com.bitblazer.tinyhands.ACTION_START_BIOMETRIC_AUTH";
        public static final String BIOMETRIC_AUTH_RESULT = "com.bitblazer.tinyhands.ACTION_BIOMETRIC_AUTH_RESULT";
        public static final String RESTORE_OVERLAY       = "com.bitblazer.tinyhands.ACTION_RESTORE_OVERLAY";
    }

    public static final class Extras {
        public static final String IS_RUNNING   = "isRunning";
        public static final String IS_LOCKED    = "isLocked";
        public static final String AUTH_SUCCESS = "authSuccess";
    }

    @SuppressWarnings("deprecation")
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        if (services == null) return false;
        for (ActivityManager.RunningServiceInfo info : services) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOverlayPermissionGranted(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
