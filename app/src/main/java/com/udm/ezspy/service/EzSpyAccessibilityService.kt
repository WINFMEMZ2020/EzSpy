package com.udm.ezspy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.udm.ezspy.MainActivity
import com.udm.ezspy.data.SESSION_TIMEOUT_MS
import com.udm.ezspy.data.SessionEndReason
import com.udm.ezspy.data.SpyRepository

/**
 * Reads window content and owns the floating grab overlay.
 *
 * The service is always bound by the system while enabled, so no foreground
 * service / notification is required. Nothing is collected unless the user
 * explicitly starts a temporary grab session from the main UI.
 */
class EzSpyAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: EzSpyAccessibilityService? = null
            private set

        private const val RETURN_FALLBACK_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: OverlayController? = null
    private var pendingUiReturn = false

    private val sessionTimeout = Runnable {
        if (overlay?.isShowing == true) {
            stopSessionAndDiscard(SessionEndReason.TIMEOUT)
        }
    }

    private val dismissFallback = Runnable {
        if (pendingUiReturn) {
            pendingUiReturn = false
            stopOverlay()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // The XML config already declares these; set them again so older or
        // vendor-patched parsers cannot silently drop interactive windows.
        serviceInfo = serviceInfo?.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        // If the service reconnects after a session lost its overlay, close the
        // stale session instead of leaving it marked as active.
        if (SpyRepository.session.value?.active == true && overlay == null) {
            stopSessionAndDiscard(SessionEndReason.SERVICE_STOPPED)
        }
    }

    /**
     * Shows the floating UI. Must be called on the main thread.
     * @param unlimited true when the user disabled the 3-minute session cap.
     * @return false when the overlay could not be created.
     */
    fun startGrab(unlimited: Boolean): Boolean {
        if (overlay?.isShowing == true) return true
        if (!Settings.canDrawOverlays(this)) return false

        stopOverlay()
        pendingUiReturn = false
        SpyRepository.clear()
        SpyRepository.startSession(unlimited)

        val controller = OverlayController(
            service = this,
            onExitRequested = { returnToMain() },
            onStopRequested = { stopSessionAndDiscard(SessionEndReason.STOPPED) },
        )
        if (!controller.start()) {
            controller.stop()
            SpyRepository.clear()
            SpyRepository.endSession(SessionEndReason.STOPPED)
            return false
        }

        overlay = controller
        handler.removeCallbacks(sessionTimeout)
        if (!unlimited) {
            handler.postDelayed(sessionTimeout, SESSION_TIMEOUT_MS)
        }
        return true
    }

    /**
     * Called from [MainActivity.onResume] / [MainActivity.onNewIntent].
     *
     * The preview's back button publishes the result before the activity is
     * brought forward, so that path only has to remove the overlay. Opening
     * the main UI manually keeps the temporary session alive; the overlay
     * panel stays visible and its back arrow remains the "complete and view"
     * action, while the main page shows status and a stop control.
     */
    fun onMainUiResumed() {
        val controller = overlay ?: run {
            pendingUiReturn = false
            return
        }

        if (pendingUiReturn) {
            pendingUiReturn = false
            stopOverlay()
        }
        // Manual open: keep the session and the floating panel untouched.
    }

    private fun returnToMain() {
        val controller = overlay ?: return
        val capture = controller.buildCapture()
        SpyRepository.publish(capture)
        SpyRepository.endSession(SessionEndReason.RETURNED)

        pendingUiReturn = true
        // Keep the overlay on screen for a moment: a visible overlay window
        // makes the background activity launch legal on Android 10+.
        launchMainActivity()
        handler.removeCallbacks(dismissFallback)
        handler.postDelayed(dismissFallback, RETURN_FALLBACK_MS)
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(intent) }
    }

    private fun stopOverlay() {
        handler.removeCallbacks(dismissFallback)
        handler.removeCallbacks(sessionTimeout)
        pendingUiReturn = false
        overlay?.stop()
        overlay = null
    }

    /**
     * Ends the current session and immediately discards its overlay and all
     * in-memory capture data. Used by the explicit stop buttons, the timeout,
     * task removal, and service shutdown.
     */
    fun stopSessionAndDiscard(reason: SessionEndReason) {
        stopOverlay()
        SpyRepository.clear()
        SpyRepository.endSession(reason)
    }

    /** Called when the main activity is finishing (back press / task removal). */
    fun onMainUiFinished() {
        stopSessionAndDiscard(SessionEndReason.ACTIVITY_FINISHED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val controller = overlay ?: return
        if (!controller.isShowing) return
        if (event?.packageName?.toString() == packageName) return
        controller.onExternalWindowChanged()
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSessionAndDiscard(SessionEndReason.TASK_REMOVED)
        super.onTaskRemoved(rootIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopSessionAndDiscard(SessionEndReason.SERVICE_STOPPED)
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopSessionAndDiscard(SessionEndReason.SERVICE_STOPPED)
        instance = null
        super.onDestroy()
    }
}
