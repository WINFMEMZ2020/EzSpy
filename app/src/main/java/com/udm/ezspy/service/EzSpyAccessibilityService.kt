package com.udm.ezspy.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.Process
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
        private const val TASK_REMOVED_KILL_DELAY_MS = 300L
        private const val TASK_WATCH_INTERVAL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: OverlayController? = null
    private var pendingUiReturn = false
    private var shuttingDown = false
    private var seenAppTask = false

    /**
     * The accessibility service itself is bound by the system, so a recents
     * swipe does not reliably deliver onTaskRemoved. Once the main UI has been
     * seen, poll the app's own task list; when it disappears the whole app is
     * torn down (and the accessibility service disabled) so nothing keeps
     * running in the background.
     */
    private val taskWatcher = object : Runnable {
        override fun run() {
            if (shuttingDown) return
            val count = appTaskCount()
            if (count > 0) {
                seenAppTask = true
                handler.postDelayed(this, TASK_WATCH_INTERVAL_MS)
            } else if (seenAppTask) {
                shutdownFromTaskRemoval()
            } else {
                handler.postDelayed(this, TASK_WATCH_INTERVAL_MS)
            }
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

        // If the service connects after the main UI is already on screen,
        // start the recents watchdog now instead of waiting for another resume.
        if (appTaskCount() > 0) onMainUiShown()
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

    /**
     * The user swiped EzSpy out of the recents list: wipe the in-memory
     * capture, remove the overlay, disable the accessibility service (so the
     * system cannot rebind it and keep the app alive) and kill the process.
     *
     * The accessibility service has to be enabled again from system settings
     * the next time the main UI is opened.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownFromTaskRemoval()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Called when the main task is removed from recents (from
     * [MainActivity.onDestroy] when finishing, or from [onTaskRemoved] when
     * the system happens to deliver it).
     *
     * Removing the accessibility service is required for a true shutdown:
     * otherwise the system would rebind it and keep the app process alive.
     */
    /**
     * Starts watching for the removal of the app's task. Called whenever the
     * main UI is resumed (after the user has actually opened the app).
     */
    fun onMainUiShown() {
        if (shuttingDown) return
        val count = appTaskCount()
        if (count > 0) seenAppTask = true
        handler.removeCallbacks(taskWatcher)
        handler.postDelayed(taskWatcher, TASK_WATCH_INTERVAL_MS)
    }

    fun shutdownFromTaskRemoval() {
        if (shuttingDown) return
        shuttingDown = true

        handler.removeCallbacksAndMessages(null)
        stopOverlay()
        SpyRepository.clear()
        runCatching { disableSelf() }
        handler.postDelayed({
            Process.killProcess(Process.myPid())
        }, TASK_REMOVED_KILL_DELAY_MS)
    }

    private fun appTaskCount(): Int = runCatching {
        getSystemService(ActivityManager::class.java)?.getAppTasks()?.size ?: 0
    }.getOrDefault(0)

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(taskWatcher)
        stopOverlay()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(taskWatcher)
        stopOverlay()
        instance = null
        super.onDestroy()
    }
}
