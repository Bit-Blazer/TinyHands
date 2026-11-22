package com.bitblazer.tinyhands;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;

public class BlockingOverlayView extends View {

    private static final long TAP_TIMEOUT_MS = 600L;
    private static final float TAP_RADIUS_DP = 80f;
    private static final long CARD_AUTO_HIDE_MS = 4000L;

    public interface OnBiometricRequestListener {
        void onBiometricRequested();
    }

    private OnBiometricRequestListener onBiometricRequestListener;

    private boolean isCardVisible = false;
    private final Runnable hideCardRunnable = new Runnable() {
        @Override
        public void run() {
            isCardVisible = false;
            invalidate();
        }
    };

    private final long[] tapTimes = new long[3];
    private float anchorX = -1f;
    private float anchorY = -1f;
    private final float tapRadiusPx;

    private final float cardSize;
    private final float cardMarginR;
    private final float cardMarginT;
    private final float cardCorner;

    private final Paint bgPaint;
    private final Drawable lockDrawable;

    public BlockingOverlayView(Context context) {
        super(context);

        tapRadiusPx = Utils.dpToPx(context, TAP_RADIUS_DP);
        cardSize = Utils.dpToPx(context, 54f);
        cardMarginR = Utils.dpToPx(context, 16f);
        cardMarginT = Utils.dpToPx(context, 48f);
        cardCorner = Utils.dpToPx(context, 16f);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.argb(0xCC, 0x15, 0x15, 0x15));

        Drawable drawable = context.getDrawable(R.drawable.ic_lock);
        if (drawable != null) {
            drawable.mutate();
            drawable.setTint(Color.WHITE);
        }
        lockDrawable = drawable;
    }

    public void setOnBiometricRequestListener(OnBiometricRequestListener listener) {
        this.onBiometricRequestListener = listener;
    }

    private RectF getCardRect() {
        float cardR = getWidth() - cardMarginR;
        float cardL = cardR - cardSize;
        float cardT = cardMarginT;
        float cardB = cardT + cardSize;
        return new RectF(cardL, cardT, cardR, cardB);
    }

    private boolean isPadlockCardTapped(float x, float y) {
        if (!isCardVisible || getWidth() <= 0) return false;
        RectF rect = getCardRect();
        float hitPadding = Utils.dpToPx(getContext(), 14f);
        RectF hitRect = new RectF(
                rect.left - hitPadding,
                rect.top - hitPadding,
                rect.right + hitPadding,
                rect.bottom + hitPadding
        );
        return hitRect.contains(x, y);
    }

    public void resetState() {
        removeCallbacks(hideCardRunnable);
        isCardVisible = false;
        anchorX = -1f;
        anchorY = -1f;
        Arrays.fill(tapTimes, 0L);
        invalidate();
    }

    public void showCardTemporarily() {
        removeCallbacks(hideCardRunnable);
        isCardVisible = true;
        invalidate();
        postDelayed(hideCardRunnable, CARD_AUTO_HIDE_MS);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action != MotionEvent.ACTION_DOWN) return true;

        long now = SystemClock.uptimeMillis();
        float x = event.getX();
        float y = event.getY();

        if (isPadlockCardTapped(x, y)) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            resetState();
            if (onBiometricRequestListener != null) {
                onBiometricRequestListener.onBiometricRequested();
            }
            return true;
        }

        if (anchorX >= 0f) {
            double dist = Math.hypot(x - anchorX, y - anchorY);
            if (dist > tapRadiusPx) {
                Arrays.fill(tapTimes, 0L);
                anchorX = x;
                anchorY = y;
                tapTimes[2] = now;
                return true;
            }
        } else {
            anchorX = x;
            anchorY = y;
        }

        tapTimes[0] = tapTimes[1];
        tapTimes[1] = tapTimes[2];
        tapTimes[2] = now;
        long span = tapTimes[2] - tapTimes[0];

        if (tapTimes[0] != 0L && span <= TAP_TIMEOUT_MS) {
            Arrays.fill(tapTimes, 0L);
            anchorX = -1f;
            anchorY = -1f;
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            showCardTemporarily();
            return true;
        }

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isCardVisible) return;

        RectF cardRect = getCardRect();
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, bgPaint);

        if (lockDrawable != null) {
            int iconSize = Utils.dpToPx(getContext(), 26f);
            int left = (int) (cardRect.centerX() - iconSize / 2f);
            int top = (int) (cardRect.centerY() - iconSize / 2f);
            lockDrawable.setBounds(left, top, left + iconSize, top + iconSize);
            lockDrawable.draw(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resetState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        resetState();
    }
}
