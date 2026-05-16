package com.bitblazer.tinyhands

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.hypot

/**
 * Minimal overlay: no tint, no border.
 * Shows a small padlock card in the top-right corner.
 * Triple-tap is position-locked (all 3 taps within TAP_RADIUS_DP of each other).
 */
class BlockingOverlayView(context: Context) : View(context) {

    companion object {
        private const val TAG           = "TinyHands/OverlayView"
        private const val TAP_TIMEOUT_MS = 600L   // all 3 taps must land within this window
        private const val TAP_RADIUS_DP  = 80f    // all taps must stay within this radius
        private const val TAP_RESET_MS   = 2500L  // reset progress after idle
    }

    var onToggleCallback: (() -> Unit)? = null

    // ── Triple-tap state ──────────────────────────────────────────────────────

    private val tapTimes   = LongArray(3) { 0L }
    private var anchorX    = -1f   // X of first tap in current sequence
    private var anchorY    = -1f   // Y of first tap in current sequence
    private var tapProgress = 0    // 0=idle, 1=one tap done, 2=two taps done
    private val tapRadiusPx = dpToPx(context, TAP_RADIUS_DP).toFloat()

    private val resetRunnable = Runnable {
        Log.d(TAG, "Tap progress reset (idle)")
        tapProgress = 0; tapTimes.fill(0L); anchorX = -1f; anchorY = -1f
        invalidate()
    }

    // ── Card geometry ─────────────────────────────────────────────────────────

    private val cardW      = dpToPx(context, 64f).toFloat()
    private val cardMarginR = dpToPx(context, 16f).toFloat()
    private val cardMarginT = dpToPx(context, 48f).toFloat()  // below status bar
    private val cardPad    = dpToPx(context, 10f).toFloat()
    private val cardCorner = dpToPx(context, 18f).toFloat()
    private val dotR       = dpToPx(context, 4.5f).toFloat()
    private val dotGap     = dpToPx(context, 13f).toFloat()   // center-to-center

    // ── Paint ─────────────────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0xCC, 0x15, 0x15, 0x15)
    }

    private val lockDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_lock)?.apply {
        DrawableCompat.setTint(this, Color.WHITE)
    }

    private val dotFilled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0xFF, 0x42, 0xA5, 0xF5)  // blue
    }

    private val dotEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dpToPx(context, 1.5f).toFloat()
        color       = Color.argb(0x88, 0xFF, 0xFF, 0xFF)
    }

    private val cardRect = RectF()

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        val now = SystemClock.uptimeMillis()
        val x = event.x; val y = event.y

        // Position gate — if tap is too far from anchor, restart sequence here
        if (anchorX >= 0f) {
            val dist = hypot(x - anchorX, y - anchorY)
            if (dist > tapRadiusPx) {
                Log.d(TAG, "Tap ${dist.toInt()}px from anchor — resetting (limit ${tapRadiusPx.toInt()}px)")
                tapTimes.fill(0L); tapProgress = 0
                anchorX = x; anchorY = y
                tapTimes[2] = now; tapProgress = 1
                removeCallbacks(resetRunnable); postDelayed(resetRunnable, TAP_RESET_MS)
                invalidate(); return true
            }
        } else {
            anchorX = x; anchorY = y   // first tap — set anchor
        }

        // Shift ring buffer
        tapTimes[0] = tapTimes[1]; tapTimes[1] = tapTimes[2]; tapTimes[2] = now
        val span = tapTimes[2] - tapTimes[0]
        Log.v(TAG, "DOWN (${x.toInt()},${y.toInt()}) span=${span}ms anchor=(${anchorX.toInt()},${anchorY.toInt()})")

        // Triple-tap check
        if (tapTimes[0] != 0L && span <= TAP_TIMEOUT_MS) {
            Log.i(TAG, "✅ Triple-tap! span=${span}ms — deactivating")
            tapTimes.fill(0L); tapProgress = 0; anchorX = -1f; anchorY = -1f
            removeCallbacks(resetRunnable)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
            onToggleCallback?.invoke() ?: Log.w(TAG, "callback is null!")
            return true
        }

        // Update progress for pill display
        tapProgress = if (tapTimes[1] != 0L && (tapTimes[2] - tapTimes[1]) <= TAP_TIMEOUT_MS) 2 else 1
        Log.d(TAG, "Tap progress: $tapProgress/3")
        removeCallbacks(resetRunnable); postDelayed(resetRunnable, TAP_RESET_MS)
        invalidate()
        return true
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dotsH   = if (tapProgress > 0) dpToPx(context, 22f).toFloat() else 0f
        val cardH   = cardPad + dpToPx(context, 28f).toFloat() + dotsH + cardPad
        val cardR   = width - cardMarginR
        val cardL   = cardR - cardW
        val cardT   = cardMarginT
        val cardB   = cardT + cardH
        val cx      = (cardL + cardR) / 2f

        cardRect.set(cardL, cardT, cardR, cardB)
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, bgPaint)

        // Lock icon
        lockDrawable?.let {
            val iconSize = dpToPx(context, 28f).toInt()
            val left = cx.toInt() - iconSize / 2
            val top = (cardT + cardPad).toInt()
            it.setBounds(left, top, left + iconSize, top + iconSize)
            it.draw(canvas)
        }

        // Progress dots (3 dots: filled = done, ring = pending)
        if (tapProgress > 0) {
            val dotY  = cardB - cardPad - dotR
            val dot1X = cx - dotGap
            val dot2X = cx
            val dot3X = cx + dotGap

            canvas.drawCircle(dot1X, dotY, dotR, dotFilled)
            canvas.drawCircle(dot2X, dotY, dotR, if (tapProgress >= 2) dotFilled else dotEmpty)
            canvas.drawCircle(dot3X, dotY, dotR, dotEmpty)
        }

        Log.v(TAG, "onDraw tapProgress=$tapProgress")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(TAG, "onSizeChanged: ${w}×${h}")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(resetRunnable)
        Log.d(TAG, "onDetachedFromWindow")
    }
}
