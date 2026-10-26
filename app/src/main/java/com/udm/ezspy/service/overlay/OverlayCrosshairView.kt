package com.udm.ezspy.service.overlay

import android.content.Context
import android.os.SystemClock
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import com.udm.ezspy.R

/**
 * Small, non-touchable crosshair that follows the finger while the user drags
 * the floating ball.
 */
class OverlayCrosshairView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private fun dp(value: Float): Float = value * density

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_crosshair_halo)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_crosshair)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_accent)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_ball_ring)
        style = Paint.Style.FILL
    }

    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var fadeStartedAt = 0L
    private val fadeRunnable = object : Runnable {
        override fun run() {
            val fraction = ((SystemClock.uptimeMillis() - fadeStartedAt).toFloat() /
                FADE_OUT_MS).coerceIn(0f, 1f)
            alpha = 1f - fraction
            if (fraction < 1f) {
                postOnAnimation(this)
            } else {
                active = false
                alpha = 1f
            }
        }
    }

    /** Show the reticle immediately for a new drag (cancels any fade). */
    fun showNow() {
        removeCallbacks(fadeRunnable)
        alpha = 1f
        active = true
    }

    /** After the finger is released the outline stays, but the reticle fades. */
    fun fadeOut() {
        removeCallbacks(fadeRunnable)
        fadeStartedAt = SystemClock.uptimeMillis()
        postOnAnimation(fadeRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return

        val centerX = width / 2f
        val centerY = height / 2f
        val arm = dp(14f)

        canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, haloPaint)
        canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, haloPaint)
        canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, linePaint)
        canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, linePaint)

        canvas.drawCircle(centerX, centerY, dp(10f), ringPaint)
        canvas.drawCircle(centerX, centerY, dp(2.6f), dotPaint)
    }

    private companion object {
        const val FADE_OUT_MS = 180L
    }
}
