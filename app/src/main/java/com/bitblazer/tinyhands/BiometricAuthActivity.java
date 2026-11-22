package com.bitblazer.tinyhands;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class BiometricAuthActivity extends Activity {

    private static final long SHIELD_TRANSITION_DELAY_MS = 250L;

    private boolean authCompleted = false;
    private boolean resultSent = false;
    private View shieldView;
    private CancellationSignal cancellationSignal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        );

        shieldView = new View(this);
        shieldView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        shieldView.setBackgroundColor(Color.TRANSPARENT);
        shieldView.setClickable(true);
        shieldView.setFocusable(true);
        setContentView(shieldView);

        showBiometricPrompt();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!authCompleted && !isFinishing()) {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            notifyResult(false);
            finishCleanly();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!authCompleted && !isFinishing()) {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            notifyResult(false);
            finishCleanly();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return true;
    }

    @SuppressWarnings("deprecation")
    private void showBiometricPrompt() {
        cancellationSignal = new CancellationSignal();

        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setConfirmationRequired(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            );
        } else {
            builder.setDeviceCredentialAllowed(true);
        }

        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                authCompleted = true;

                Intent stopIntent = new Intent(BiometricAuthActivity.this, OverlayService.class);
                stopIntent.setAction(Utils.Actions.STOP_SERVICE);
                startService(stopIntent);

                notifyResult(true);
                finishCleanly();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                authCompleted = false;
                notifyResult(false);
                finishWithShieldDelay();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        };

        try {
            BiometricPrompt biometricPrompt = builder.build();
            biometricPrompt.authenticate(cancellationSignal, getMainExecutor(), callback);
        } catch (Exception e) {
            notifyResult(false);
            finishCleanly();
        }
    }

    private void finishCleanly() {
        if (!isFinishing() && !isDestroyed()) {
            finish();
            applyZeroTransition();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyZeroTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    private void finishWithShieldDelay() {
        if (isFinishing()) return;
        shieldView.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishCleanly();
            }
        }, SHIELD_TRANSITION_DELAY_MS);
    }

    private void notifyResult(boolean success) {
        if (resultSent) {
            return;
        }
        resultSent = true;
        Intent intent = new Intent(Utils.Actions.BIOMETRIC_AUTH_RESULT);
        intent.putExtra(Utils.Extras.AUTH_SUCCESS, success);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    protected void onDestroy() {
        if (!authCompleted) {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            notifyResult(false);
        }
        super.onDestroy();
    }
}
