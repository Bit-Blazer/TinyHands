package com.bitblazer.tinyhands

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Full-screen transparent overlay view that:
 *  - Consumes all touch events when locked (WindowManager flag handles pass-through when unlocked)
 *  - Draws a subtle lock-state indicator (pill badge) in the top-right corner
 *  - Detects a triple-tap within [TAP_TIMEOUT_MS] to toggle lock state
 */
class BlockingOverlayView(context: Context) : View(context) {

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called when a valid triple-tap is detected. Set by OverlayService. */
    var onToggleCallback: (() -> Unit)? = null

    /** Current lock state — drives visual rendering. */
    private var isLocked: Boolean = false

    /** Whether the pill badge is currently visible. */
    private var pillVisible: Boolean = false

    // ── Triple-tap detection ──────────────────────────────────────────────────

    private val tapTimes = LongArray(3) { 0L }
    private val TAP_TIMEOUT_MS = 600L

    // ── Paint objects ─────────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18FF6600")   // faint amber tint (locked only)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFA040")   // soft amber border (locked only)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(context, 3f).toFloat()
    }

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000")   // dark semi-transparent pill
        style = Paint.Style.FILL
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dpToPx(context, 13f).toFloat()
        isFakeBoldText = true
        textAlign = Paint.Align.LEFT
    }

    private val pillRect = RectF()
    private val pillCornerRadius = dpToPx(context, 14f).toFloat()
    private val pillPaddingH = dpToPx(context, 12f).toFloat()
    private val pillPaddingV = dpToPx(context, 7f).toFloat()
    private val pillMarginTop = dpToPx(context, 48f).toFloat()
    private val pillMarginRight = dpToPx(context, 12f).toFloat()

    // ── Runnable for auto-hiding the pill ─────────────────────────────────────

    private val hidePillRunnable = Runnable {
        pillVisible = false
        invalidate()
    }

    // ── Public methods ────────────────────────────────────────────────────────

    /**
     * Update the visual lock state and show the pill indicator briefly.
     * Called by OverlayService after toggling lock in WindowManager.
     */
    fun setLocked(locked: Boolean) {
        isLocked = locked
        showPill(if (locked) 4000L else 3000L)
        invalidate()
    }

    /** Show the pill indicator for [durationMs] ms. Call this immediately on state change. */
    private fun showPill(durationMs: Long) {
        removeCallbacks(hidePillRunnable)
        pillVisible = true
        invalidate()
        postDelayed(hidePillRunnable, durationMs)
    }

    // ── Touch event handling ──────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            tapTimes[0] = tapTimes[1]
            tapTimes[1] = tapTimes[2]
            tapTimes[2] = SystemClock.uptimeMillis()

            val span = tapTimes[2] - tapTimes[0]
            if (tapTimes[0] != 0L && span <= TAP_TIMEOUT_MS) {
                onTripleTapDetected()
                tapTimes.fill(0L)
            }
        }
        // Always consume — WindowManager.FLAG_NOT_TOUCHABLE handles pass-through in unlocked mode
        return true
    }

    private fun onTripleTapDetected() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        // Show pill immediately as feedback before service updates lock state
        showPill(400L)
        onToggleCallback?.invoke()
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (isLocked) {
            // Faint amber background tint
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            // Amber border around entire screen
            val half = borderPaint.strokeWidth / 2f
            canvas.drawRect(half, half, w - half, h - half, borderPaint)
        }

        if (pillVisible) {
            drawPill(canvas, w)
        }
    }

    private fun drawPill(canvas: Canvas, viewWidth: Float) {
        val text = if (isLocked) "🔒 LOCKED" else "🔓 Tap 3x to lock"

        val textWidth = pillTextPaint.measureText(text)
        val pillWidth = textWidth + pillPaddingH * 2
        val pillHeight = pillTextPaint.textSize + pillPaddingV * 2

        val right = viewWidth - pillMarginRight
        val left = right - pillWidth
        val top = pillMarginTop
        val bottom = top + pillHeight

        pillRect.set(left, top, right, bottom)
        canvas.drawRoundRect(pillRect, pillCornerRadius, pillCornerRadius, pillBgPaint)

        // Vertically center text in pill
        val textY = top + pillPaddingV + pillTextPaint.textSize - pillTextPaint.descent()
        canvas.drawText(text, left + pillPaddingH, textY, pillTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(hidePillRunnable)
    }
}
