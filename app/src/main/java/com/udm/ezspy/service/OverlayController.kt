package com.udm.ezspy.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import com.udm.ezspy.R
import com.udm.ezspy.data.Capture
import com.udm.ezspy.data.NodeSnapshot
import com.udm.ezspy.databinding.ItemOverlaySummaryBinding
import com.udm.ezspy.databinding.OverlayPanelBinding
import com.udm.ezspy.service.overlay.OverlayBallView
import com.udm.ezspy.service.overlay.OverlayCrosshairView
import com.udm.ezspy.service.overlay.OverlayHighlightView
import com.udm.ezspy.util.dp
import com.udm.ezspy.util.screenSize
import kotlin.math.max
import kotlin.math.min

/**
 * Owns the floating grab UI:
 *
 *  - a fixed translucent ball (touchable, drag handle),
 *  - a crosshair which follows the finger (not touchable),
 *  - a gray node outline (not touchable),
 *  - a draggable preview card with a back button.
 *
 * The ball and the card are [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * windows (hence the overlay permission), while the passive decorations use
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] so that touches pass
 * through them and they cannot obscure the inspected app.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onExitRequested: () -> Unit,
) {

    private val windowManager: WindowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val hitTester = NodeHitTester(service)
    private val snapshotFactory = NodeSnapshotFactory(service.packageManager)

    private var ballView: OverlayBallView? = null
    private var crosshairView: OverlayCrosshairView? = null
    private var highlightView: OverlayHighlightView? = null
    private var panelBinding: OverlayPanelBinding? = null

    private var ballParams: WindowManager.LayoutParams? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var highlightParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var showing = false
    private var exitRequested = false
    private var dragInProgress = false
    private var pendingPoint: PointF? = null
    private var lastPoint: PointF? = null

    private var currentKey: String? = null

    private val hitRunnable = Runnable { performHitTest() }

    private fun dp(value: Int): Int = service.dp(value.toFloat())

    val isShowing: Boolean get() = showing

    /**
     * Re-hit-tests the crosshair and captures the complete hierarchy of the
     * window that contains the picked node. Called when the user leaves grab
     * mode; may take a moment for very large windows.
     */
    fun buildCapture(): Capture? {
        val point = lastPoint ?: return null
        val node = runCatching {
            hitTester.findNodeAt(point.x.toInt(), point.y.toInt())
        }.getOrNull() ?: return null
        return runCatching { snapshotFactory.buildCapture(node) }.getOrNull()
    }

    fun start(): Boolean {
        if (showing) return true
        if (!Settings.canDrawOverlays(service)) return false

        val screen = service.screenSize()
        exitRequested = false
        dragInProgress = false
        pendingPoint = null
        lastPoint = null
        currentKey = null

        highlightView = OverlayHighlightView(service).apply { visibility = View.INVISIBLE }
        highlightParams = baseParams(
            width = 1,
            height = 1,
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            touchable = false,
            allowOutsideScreen = true,
        ).apply {
            x = -100
            y = -100
            title = "EzSpyHighlight"
        }

        val themedContext = android.view.ContextThemeWrapper(service, R.style.Theme_EzSpy_Overlay)
        panelBinding = OverlayPanelBinding.inflate(LayoutInflater.from(themedContext))
        val panelWidth = min(dp(300), max(dp(200), screen.x - dp(120)))
        val panelHeight = min(dp(330), max(dp(200), (screen.y * 0.46f).toInt()))
        panelParams = baseParams(
            width = panelWidth,
            height = panelHeight,
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            touchable = true,
            allowOutsideScreen = true,
        ).apply {
            x = max(0, screen.x - panelWidth - dp(16))
            y = max(dp(24), (screen.y * 0.16f).toInt())
            title = "EzSpyPreview"
        }

        ballView = OverlayBallView(service)
        val ballSize = dp(64)
        ballParams = baseParams(
            width = ballSize,
            height = ballSize,
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            touchable = true,
        ).apply {
            x = dp(12)
            y = max(dp(24), (screen.y * 0.24f).toInt())
            title = "EzSpyBall"
        }

        val crosshairSize = dp(56)
        crosshairView = OverlayCrosshairView(service)
        crosshairParams = baseParams(
            width = crosshairSize,
            height = crosshairSize,
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            touchable = false,
            allowOutsideScreen = true,
        ).apply {
            x = -200
            y = -200
            title = "EzSpyCrosshair"
        }

        // Added in this order so the decorations stay below the card and ball.
        if (!addWindow(highlightView!!, highlightParams!!)) {
            stop()
            return false
        }
        if (!addWindow(panelBinding!!.root, panelParams!!)) {
            stop()
            return false
        }
        if (!addWindow(ballView!!, ballParams!!)) {
            stop()
            return false
        }
        if (!addWindow(crosshairView!!, crosshairParams!!)) {
            stop()
            return false
        }

        setupPanel()
        setupBall()
        showPanelNode(null)
        showing = true
        return true
    }

    fun stop() {
        showing = false
        exitRequested = false
        dragInProgress = false
        handler.removeCallbacks(hitRunnable)
        pendingPoint = null
        lastPoint = null
        currentKey = null

        crosshairView?.let { removeWindow(it) }
        ballView?.let { removeWindow(it) }
        panelBinding?.let { removeWindow(it.root) }
        highlightView?.let { removeWindow(it) }

        crosshairView = null
        ballView = null
        panelBinding = null
        highlightView = null
        crosshairParams = null
        ballParams = null
        panelParams = null
        highlightParams = null
    }

    /** Called when another window changes while grab mode is active. */
    fun onExternalWindowChanged() {
        if (!showing || dragInProgress) return
        val point = lastPoint ?: return
        scheduleHitTest(point.x, point.y, EXTERNAL_REFRESH_DELAY_MS)
    }

    fun onConfigurationChanged() {
        if (!showing) return
        val screen = service.screenSize()

        panelParams?.let { params ->
            val width = min(dp(300), max(dp(200), screen.x - dp(120)))
            val height = min(dp(330), max(dp(200), (screen.y * 0.46f).toInt()))
            params.width = width
            params.height = height
            // Keep x/y: an intentionally parked off-screen panel stays parked.
            panelBinding?.root?.let { updateWindow(it, params) }
        }
        ballParams?.let { params ->
            params.x = params.x.coerceIn(0, max(0, screen.x - params.width))
            params.y = params.y.coerceIn(0, max(0, screen.y - params.height))
            ballView?.let { updateWindow(it, params) }
        }
        if (!dragInProgress) {
            lastPoint?.let { scheduleHitTest(it.x, it.y, EXTERNAL_REFRESH_DELAY_MS) }
        }
    }

    private fun setupBall() {
        ballView?.listener = object : OverlayBallView.Listener {
            override fun onCrosshairStart(x: Float, y: Float) {
                dragInProgress = true
                crosshairView?.showNow()
                updateCrosshair(x, y)
                lastPoint = PointF(x, y)
                scheduleHitTest(x, y, 0)
            }

            override fun onCrosshairMove(x: Float, y: Float) {
                updateCrosshair(x, y)
                lastPoint = PointF(x, y)
                scheduleHitTest(x, y, HIT_TEST_INTERVAL_MS)
            }

            override fun onCrosshairEnd(x: Float, y: Float) {
                updateCrosshair(x, y)
                lastPoint = PointF(x, y)
                dragInProgress = false
                scheduleHitTest(x, y, 0)
                // Keep the selected outline and preview, but fade the reticle.
                crosshairView?.fadeOut()
            }

            override fun onTap() {
                recallPanelIfOffScreen()
            }
        }
    }

    /**
     * The panel can be moved completely off-screen. Tapping the fixed ball is
     * the recovery gesture: it pulls the panel back to its default position.
     */
    private fun recallPanelIfOffScreen() {
        val params = panelParams ?: return
        if (!showing) return
        val screen = service.screenSize()
        val fullyVisible = params.x >= 0 &&
            params.y >= 0 &&
            params.x + params.width <= screen.x &&
            params.y + params.height <= screen.y
        if (fullyVisible) return

        params.x = max(0, screen.x - params.width - dp(16))
        params.y = max(dp(24), (screen.y * 0.16f).toInt())
        panelBinding?.root?.let { updateWindow(it, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPanel() {
        val binding = panelBinding ?: return
        binding.backButton.setOnClickListener { requestExit() }

        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        binding.titleBar.setOnTouchListener { _, event ->
            val params = panelParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return@setOnTouchListener false
                    // Like a real window, the panel may be dragged partly (or
                    // completely) outside the display.
                    params.x = startX + (event.rawX - startRawX).toInt()
                    params.y = startY + (event.rawY - startRawY).toInt()
                    panelBinding?.root?.let { updateWindow(it, params) }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun requestExit() {
        if (!showing || exitRequested) return
        exitRequested = true
        onExitRequested()
    }

    private fun updateCrosshair(x: Float, y: Float) {
        val view = crosshairView ?: return
        val params = crosshairParams ?: return
        params.x = (x - params.width / 2f).toInt()
        params.y = (y - params.height / 2f).toInt()
        updateWindow(view, params)
    }

    private fun scheduleHitTest(x: Float, y: Float, delayMs: Long) {
        pendingPoint = PointF(x, y)
        handler.removeCallbacks(hitRunnable)
        if (delayMs <= 0L) {
            handler.post(hitRunnable)
        } else {
            handler.postDelayed(hitRunnable, delayMs)
        }
    }

    private fun performHitTest() {
        if (!showing) return
        val point = pendingPoint ?: lastPoint ?: return

        val node = runCatching {
            hitTester.findNodeAt(point.x.toInt(), point.y.toInt())
        }.getOrNull()

        if (node == null) {
            clearSelection()
            return
        }

        val key = nodeKey(node)
        if (key == currentKey) return

        val chain = runCatching { snapshotFactory.buildChain(node) }.getOrNull()
        if (chain.isNullOrEmpty()) {
            clearSelection()
            return
        }

        currentKey = key
        val picked = chain.last()
        showHighlight(picked.bounds)
        showPanelNode(picked)
    }

    private fun nodeKey(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        return "${node.windowId}:${node.hashCode()}:$bounds"
    }

    private fun clearSelection() {
        if (currentKey == null) return
        currentKey = null
        hideHighlight()
        showPanelNode(null)
    }

    private fun showHighlight(bounds: Rect) {
        val view = highlightView ?: return
        val params = highlightParams ?: return
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            hideHighlight()
            return
        }
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width()
        params.height = bounds.height()
        view.visibility = View.VISIBLE
        updateWindow(view, params)
    }

    private fun hideHighlight() {
        val view = highlightView ?: return
        val params = highlightParams ?: return
        view.visibility = View.INVISIBLE
        params.x = -100
        params.y = -100
        params.width = 1
        params.height = 1
        updateWindow(view, params)
    }

    private fun showPanelNode(node: NodeSnapshot?) {
        val binding = panelBinding ?: return
        binding.summaryContainer.removeAllViews()
        binding.emptyState.isVisible = node == null
        binding.summaryScroll.isVisible = node != null
        if (node == null) return

        addSummaryRow(
            binding,
            service.getString(R.string.overlay_class),
            node.className ?: node.displayTitle,
        )
        addSummaryRow(
            binding,
            service.getString(R.string.overlay_text),
            node.text?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.takeIf { it.isNotBlank() }
                ?: "—",
        )
        addSummaryRow(
            binding,
            service.getString(R.string.overlay_id),
            node.shortViewId?.let { "id/$it" } ?: "—",
        )
        addSummaryRow(
            binding,
            service.getString(R.string.overlay_bounds),
            node.boundsText,
        )
        addSummaryRow(
            binding,
            service.getString(R.string.overlay_package),
            node.packageName ?: "—",
        )
    }

    private fun addSummaryRow(binding: OverlayPanelBinding, label: String, value: String) {
        val row = ItemOverlaySummaryBinding.inflate(
            LayoutInflater.from(binding.summaryContainer.context),
            binding.summaryContainer,
            false,
        )
        row.label.text = label
        row.value.text = value
        binding.summaryContainer.addView(row.root)
    }

    private fun baseParams(
        width: Int,
        height: Int,
        type: Int,
        touchable: Boolean,
        allowOutsideScreen: Boolean = false,
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (allowOutsideScreen) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun addWindow(view: View, params: WindowManager.LayoutParams): Boolean =
        runCatching { windowManager.addView(view, params) }.isSuccess

    private fun updateWindow(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removeWindow(view: View) {
        runCatching { windowManager.removeView(view) }
    }

    private companion object {
        const val HIT_TEST_INTERVAL_MS = 28L
        const val EXTERNAL_REFRESH_DELAY_MS = 160L
    }
}
