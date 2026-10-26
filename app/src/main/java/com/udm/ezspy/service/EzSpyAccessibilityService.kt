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
import com.udm.ezspy.data.SpyRepository

/**
 * Reads window content and owns the floating grab overlay.
 *
 * The service is always bound by the system while enabled, so no foreground
 * service / notification is required. Nothing is collected unless the user
 * explicitly starts grab mode from the main UI.
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
    }

    /**
     * Shows the floating UI. Must be called on the main thread.
     * @return false when the overlay could not be created.
     */
    fun startGrab(): Boolean {
        if (overlay?.isShowing == true) return true
        if (!Settings.canDrawOverlays(this)) return false

        stopOverlay()
        pendingUiReturn = false

        val controller = OverlayController(this) { returnToMain() }
        if (!controller.start()) {
            controller.stop()
            return false
        }

        overlay = controller
        SpyRepository.clear()
        return true
    }

    /**
     * Called from [MainActivity.onResume] / [MainActivity.onNewIntent].
     *
     * There are two cases:
     *  - the user pressed the preview's back button: the result is already
     *    published and the overlay only has to be removed;
     *  - the user opened EzSpy manually while grabbing: keep the current
     *    selection and publish it before removing the overlay.
     */
    fun onMainUiResumed() {
        val controller = overlay
        if (controller == null) {
            pendingUiReturn = false
            return
        }

        if (pendingUiReturn) {
            pendingUiReturn = false
            stopOverlay()
            return
        }

        val capture = controller.buildCapture()
        stopOverlay()
        SpyRepository.publish(capture)
    }

    private fun returnToMain() {
        val controller = overlay ?: return
        val capture = controller.buildCapture()
        SpyRepository.publish(capture)

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
        pendingUiReturn = false
        overlay?.stop()
        overlay = null
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

    override fun onUnbind(intent: Intent?): Boolean {
        stopOverlay()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopOverlay()
        instance = null
        super.onDestroy()
    }
}
