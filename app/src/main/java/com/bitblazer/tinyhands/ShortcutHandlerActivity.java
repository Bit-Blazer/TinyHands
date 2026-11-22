package com.bitblazer.tinyhands;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class ShortcutHandlerActivity extends Activity {

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }

        if (!Utils.isOverlayPermissionGranted(this)) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(mainIntent);
        } else if (OverlayService.isRunning) {
            Intent stopIntent = new Intent(this, OverlayService.class);
            stopIntent.setAction(Utils.Actions.STOP_SERVICE);
            startService(stopIntent);
        } else {
            startForegroundService(new Intent(this, OverlayService.class));
        }

        finish();
    }
}
