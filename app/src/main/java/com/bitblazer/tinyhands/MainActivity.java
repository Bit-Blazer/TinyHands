package com.bitblazer.tinyhands;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY_PERM = 1001;

    private LinearLayout layoutPermission;
    private LinearLayout layoutControls;
    private Button btnToggle;
    private TextView tvTip;
    private BroadcastReceiver stateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.parseColor("#121316"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = Utils.dpToPx(this, 24);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText(R.string.app_title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        root.addView(tvTitle);

        // Subtitle
        TextView tvSub = new TextView(this);
        tvSub.setText(R.string.app_subtitle);
        tvSub.setTextColor(Color.parseColor("#9E9E9E"));
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.topMargin = Utils.dpToPx(this, 6);
        subLp.bottomMargin = Utils.dpToPx(this, 32);
        root.addView(tvSub, subLp);

        // Permission Box (shown only if permission missing)
        layoutPermission = new LinearLayout(this);
        layoutPermission.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable permBg = new GradientDrawable();
        permBg.setCornerRadius(Utils.dpToPx(this, 16));
        permBg.setColor(Color.parseColor("#2A1E00"));
        permBg.setStroke(Utils.dpToPx(this, 1), Color.parseColor("#FFC107"));
        layoutPermission.setBackground(permBg);
        int permPad = Utils.dpToPx(this, 16);
        layoutPermission.setPadding(permPad, permPad, permPad, permPad);

        TextView tvPermWarning = new TextView(this);
        tvPermWarning.setText(R.string.permission_warning);
        tvPermWarning.setTextColor(Color.parseColor("#FFD54F"));
        tvPermWarning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvPermWarning.setGravity(Gravity.CENTER);
        layoutPermission.addView(tvPermWarning);

        Button btnGrant = new Button(this);
        btnGrant.setText(R.string.btn_grant_permission);
        btnGrant.setTextColor(Color.BLACK);
        GradientDrawable btnGrantBg = new GradientDrawable();
        btnGrantBg.setCornerRadius(Utils.dpToPx(this, 12));
        btnGrantBg.setColor(Color.parseColor("#FFC107"));
        btnGrant.setBackground(btnGrantBg);
        LinearLayout.LayoutParams grantLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dpToPx(this, 48)
        );
        grantLp.topMargin = Utils.dpToPx(this, 12);
        btnGrant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, REQ_OVERLAY_PERM);
            }
        });
        layoutPermission.addView(btnGrant, grantLp);

        LinearLayout.LayoutParams permBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permBoxLp.bottomMargin = Utils.dpToPx(this, 24);
        root.addView(layoutPermission, permBoxLp);

        // Controls container (toggle button & tip)
        layoutControls = new LinearLayout(this);
        layoutControls.setOrientation(LinearLayout.VERTICAL);

        btnToggle = new Button(this);
        btnToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        btnToggle.setTypeface(Typeface.DEFAULT_BOLD);
        btnToggle.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dpToPx(this, 58)
        );
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!OverlayService.isRunning) {
                    startForegroundService(new Intent(MainActivity.this, OverlayService.class));
                } else {
                    Intent stopIntent = new Intent(MainActivity.this, OverlayService.class);
                    stopIntent.setAction(Utils.Actions.STOP_SERVICE);
                    startService(stopIntent);
                }
                refreshState();
            }
        });
        layoutControls.addView(btnToggle, btnLp);

        tvTip = new TextView(this);
        tvTip.setText("Triple-tap screen or tap padlock to unlock");
        tvTip.setTextColor(Color.parseColor("#757575"));
        tvTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tipLp.topMargin = Utils.dpToPx(this, 16);
        layoutControls.addView(tvTip, tipLp);

        root.addView(layoutControls);
        scrollView.addView(root);
        setContentView(scrollView);

        registerStateReceiver();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY_PERM) {
            refreshState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    @Override
    protected void onDestroy() {
        unregisterStateReceiver();
        super.onDestroy();
    }

    private void refreshState() {
        boolean hasPerm = Utils.isOverlayPermissionGranted(this);
        boolean running = OverlayService.isRunning;

        layoutPermission.setVisibility(hasPerm ? View.GONE : View.VISIBLE);
        layoutControls.setVisibility(hasPerm ? View.VISIBLE : View.GONE);

        if (!hasPerm) return;

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Utils.dpToPx(this, 16));

        if (running) {
            btnToggle.setText(R.string.btn_disable);
            bg.setColor(Color.parseColor("#BA1A1A"));
            tvTip.setVisibility(View.VISIBLE);
        } else {
            btnToggle.setText(R.string.btn_enable);
            bg.setColor(Color.parseColor("#2E7D32"));
            tvTip.setVisibility(View.GONE);
        }
        btnToggle.setBackground(bg);
    }

    private void registerStateReceiver() {
        IntentFilter filter = new IntentFilter(Utils.Actions.STATE_CHANGED);
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshState();
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
            } catch (IllegalArgumentException ignored) {}
            stateReceiver = null;
        }
    }
}
