package com.udm.ezspy.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import com.udm.ezspy.service.EzSpyAccessibilityService

object PermissionUtils {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, EzSpyAccessibilityService::class.java)

        val manager = context.getSystemService(AccessibilityManager::class.java)
        if (manager != null) {
            val enabled = runCatching {
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            }.getOrDefault(emptyList())

            if (enabled.any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo
                    serviceInfo != null &&
                        serviceInfo.packageName == expected.packageName &&
                        serviceInfo.name == expected.className
                }
            ) {
                return true
            }
        }

        // Some OEM builds report an empty list even though the service is on.
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val full = expected.flattenToString()
        val short = expected.flattenToShortString()
        return raw.split(':').any { it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true) }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** @return true when a settings screen could be opened. */
    fun openAccessibilitySettings(context: Context): Boolean =
        startActivitySafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** @return true when a settings screen could be opened. */
    fun openOverlaySettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
        return startActivitySafely(context, intent)
    }

    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (opened) return true

        val fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        )
        if (context !is Activity) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(fallback) }.isSuccess
    }
}
