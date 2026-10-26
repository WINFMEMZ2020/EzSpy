package com.udm.ezspy.ui.tree

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.udm.ezspy.R

/**
 * Row background plus code-editor-style indent guides.
 *
 * One vertical guide is drawn for every ancestor level of the row (and for the
 * row's own bullet column). The guides are painted below the dot/text because
 * this drawable is the row background, not a foreground decoration.
 */
class TreeItemBackground(
    context: Context,
    private val depth: Int,
    private val selected: Boolean,
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val baseIndent = TreeMetrics.BASE_INDENT_DP * density
    private val step = TreeMetrics.INDENT_STEP_DP * density
    private val dotCenter = 4f * density
    private val corner = 12f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_accent_container)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(
            context,
            if (selected) R.color.color_tree_guide_selected else R.color.color_tree_guide,
        )
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        if (selected) {
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                corner,
                corner,
                fillPaint,
            )
        }

        val contentStart = bounds.left + baseIndent + depth * step
        val top = bounds.top + 0.5f * density
        val bottom = bounds.bottom - 0.5f * density
        for (level in 0..depth) {
            val x = contentStart + dotCenter - level * step
            if (x < bounds.left + 1f || x > bounds.right - 1f) continue
            canvas.drawLine(x, top, x, bottom, guidePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        guidePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        guidePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
