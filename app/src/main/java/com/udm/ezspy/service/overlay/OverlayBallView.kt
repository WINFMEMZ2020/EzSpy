package com.udm.ezspy.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.udm.ezspy.R
import kotlin.math.hypot
import kotlin.math.min

/**
 * The fixed, draggable "handle" of grab mode. Touching and dragging the ball
 * moves the crosshair, the ball itself never moves.
 */
class OverlayBallView(context: Context) : View(context) {

    interface Listener {
        fun onCrosshairStart(x: Float, y: Float)
        fun onCrosshairMove(x: Float, y: Float)
        fun onCrosshairEnd(x: Float, y: Float)
        fun onTap()
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density

    private fun dp(value: Float): Float = value * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_ball_bg)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_ball_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_ball_ring)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    private val crosshairHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_crosshair_halo)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_crosshair)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_ball_ring)
        style = Paint.Style.FILL
    }

    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var pressed = false

    init {
        isClickable = true
        contentDescription = context.getString(R.string.overlay_ball_desc)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastRawX = downX
                lastRawY = downY
                dragging = false
                pressed = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (!dragging) {
                    val distance = hypot(event.rawX - downX, event.rawY - downY)
                    if (distance > touchSlop) {
                        dragging = true
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        listener?.onCrosshairStart(event.rawX, event.rawY)
                    }
                }
                if (dragging) {
                    listener?.onCrosshairMove(event.rawX, event.rawY)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    listener?.onCrosshairEnd(event.rawX, event.rawY)
                } else {
                    listener?.onTap()
                }
                dragging = false
                pressed = false
                performClick()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) listener?.onCrosshairEnd(lastRawX, lastRawY)
                dragging = false
                pressed = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - dp(5f)

        canvas.drawCircle(centerX, centerY + dp(1.5f), radius + dp(1f), shadowPaint)
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        if (pressed || dragging) {
            canvas.drawCircle(centerX, centerY, radius + dp(2.5f), ringPaint)
        }

        val arm = dp(12.5f)
        canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, crosshairHaloPaint)
        canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, crosshairHaloPaint)
        canvas.drawLine(centerX - arm, centerY, centerX + arm, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - arm, centerX, centerY + arm, crosshairPaint)
        canvas.drawCircle(centerX, centerY, dp(2.4f), dotPaint)
    }
}
