package com.udm.ezspy.ui.tree

/** Shared geometry of the tree rows (kept in px-free dp units). */
object TreeMetrics {
    const val BASE_INDENT_DP = 12
    const val INDENT_STEP_DP = 20

    /** Deep trees are panned horizontally instead of squeezed further. */
    const val MAX_INDENT_LEVELS = 40

    /** Room reserved to the right of the deepest node for its title. */
    const val TITLE_AREA_DP = 300
}
