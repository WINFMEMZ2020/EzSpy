package com.udm.ezspy.ui.tree

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * A `HorizontalScrollView` measures its child with an UNSPECIFIED width, which
 * makes a regular RecyclerView shrink to the viewport and destroys horizontal
 * panning. This subclass reports the requested content width instead.
 */
class PanningRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    var contentWidth: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (View.MeasureSpec.getMode(widthSpec) == View.MeasureSpec.UNSPECIFIED &&
            contentWidth > 0
        ) {
            super.onMeasure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                heightSpec,
            )
        } else {
            super.onMeasure(widthSpec, heightSpec)
        }
    }
}
