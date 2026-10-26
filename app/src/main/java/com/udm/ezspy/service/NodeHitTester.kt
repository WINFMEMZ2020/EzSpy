package com.udm.ezspy.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Finds the deepest accessible node under a screen coordinate.
 *
 * Accessibility services cannot perform hit testing directly, so the window
 * tree is walked. [AccessibilityService.getWindows] returns windows ordered by
 * descending layer (topmost first), which is exactly the order needed here.
 * Overlay windows owned by EzSpy are ignored so the crosshair always "looks
 * through" the preview panel and the ball.
 */
class NodeHitTester(
    private val service: AccessibilityService,
) {

    fun findNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()

        for (window in windows) {
            val windowBounds = Rect().also { window.getBoundsInScreen(it) }
            if (windowBounds.width() <= 0 || windowBounds.height() <= 0) continue
            if (x < windowBounds.left || x >= windowBounds.right ||
                y < windowBounds.top || y >= windowBounds.bottom
            ) {
                continue
            }

            val root = runCatching { window.root }.getOrNull() ?: continue
            if (isOwnWindow(root)) continue

            val hit = deepestNodeAt(root, x, y)
            if (hit != null) return hit
        }

        // Fallback for devices/OEMs where the window list is unavailable.
        val activeRoot = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        if (isOwnWindow(activeRoot)) return null
        return deepestNodeAt(activeRoot, x, y)
    }

    private fun isOwnWindow(node: AccessibilityNodeInfo): Boolean =
        runCatching { node.packageName?.toString() }.getOrNull() == service.packageName

    private fun deepestNodeAt(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestDepth = -1

        val stack = ArrayDeque<NodeWithDepth>()
        stack.addLast(NodeWithDepth(root, 0))
        var visited = 0
        val rect = Rect()

        while (stack.isNotEmpty() && visited < MAX_VISITED) {
            val current = stack.removeLast()
            visited++
            if (current.depth > MAX_DEPTH) continue

            val node = current.node
            rect.setEmpty()
            runCatching { node.getBoundsInScreen(rect) }
            if (rect.width() <= 0 || rect.height() <= 0) continue
            if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) continue
            if (!isVisible(node)) continue

            if (current.depth > bestDepth) {
                bestDepth = current.depth
                best = node
            }

            // Push in natural order: the last (topmost) child is popped first,
            // so it wins when two children are at the same depth.
            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (index in 0 until childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                stack.addLast(NodeWithDepth(child, current.depth + 1))
            }
        }

        return best
    }

    private fun isVisible(node: AccessibilityNodeInfo): Boolean =
        runCatching { node.isVisibleToUser }.getOrDefault(true)

    private data class NodeWithDepth(
        val node: AccessibilityNodeInfo,
        val depth: Int,
    )

    private companion object {
        const val MAX_VISITED = 3000
        const val MAX_DEPTH = 80
    }
}
