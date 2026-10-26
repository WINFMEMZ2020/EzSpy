package com.udm.ezspy.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.udm.ezspy.R
import com.udm.ezspy.util.dp

/**
 * Gray outline drawn exactly over the bounds of the currently picked node.
 * The window is sized to those bounds and is not touchable.
 */
class OverlayHighlightView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val cornerRadius = 4f * density

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_highlight_halo)
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_highlight)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = 2.25f * density
        rect.set(inset, inset, width - inset, height - inset)
        if (rect.width() <= 0f || rect.height() <= 0f) return

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, haloPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outlinePaint)
    }
}
