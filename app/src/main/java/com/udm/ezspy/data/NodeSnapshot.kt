package com.udm.ezspy.data

import android.graphics.Rect

/**
 * Immutable snapshot of an [android.view.accessibility.AccessibilityNodeInfo].
 *
 * The accessibility service never hands live node objects to the UI: nodes may
 * become stale at any moment, so everything the UI needs is copied here.
 */
data class NodeSnapshot(
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val packageName: String?,
    val appLabel: String?,
    val bounds: Rect,
    /** Depth inside the captured window tree; the window root is 0. */
    val depth: Int,
    /** True for password fields; their content is masked unless the user opts in. */
    val isSensitive: Boolean,
    val propertyGroups: List<PropertyGroup>,
) {
    val simpleClassName: String
        get() = className
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "未知节点"

    val displayTitle: String
        get() = simpleClassName

    val shortViewId: String?
        get() = viewId
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.contains(":id/")) it.substringAfter(":id/") else it }
            ?.takeIf { it.isNotBlank() }

    fun textForDisplay(showSensitive: Boolean): String? =
        if (isSensitive && !showSensitive) MASKED_TEXT else text

    fun contentDescriptionForDisplay(showSensitive: Boolean): String? =
        if (isSensitive && !showSensitive) MASKED_TEXT else contentDescription

    /** Secondary line used in the node tree. */
    fun secondaryLabel(showSensitive: Boolean): String =
        shortViewId?.let { "id/$it" }
            ?: textForDisplay(showSensitive)?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { singleLine(it) }
            ?: contentDescriptionForDisplay(showSensitive)?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { singleLine(it) }
            ?: "无文本 / ID"

    val boundsText: String
        get() = "${bounds.left}, ${bounds.top} – ${bounds.right}, ${bounds.bottom} · " +
            "${bounds.width()}×${bounds.height()}"

    private fun singleLine(value: String): String {
        val oneLine = value.replace('\n', ' ').replace('\r', ' ')
        return if (oneLine.length <= 80) oneLine else oneLine.take(79) + "…"
    }

    companion object {
        const val MASKED_TEXT = "••••"
    }
}

/**
 * One complete grab result: every node of the picked window in pre-order, plus
 * the index of the node the crosshair pointed at.
 */
data class Capture(
    val nodes: List<NodeSnapshot>,
    val pickedIndex: Int,
    val targetPackage: String?,
    val targetLabel: String?,
    val windowTitle: String?,
    val truncated: Boolean,
) {
    val picked: NodeSnapshot? get() = nodes.getOrNull(pickedIndex)
}
