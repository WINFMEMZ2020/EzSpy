package com.udm.ezspy.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import kotlin.math.roundToInt

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

/** Full display size in pixels, system bars included. */
fun Context.screenSize(): Point {
    val windowManager = getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.currentWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        val point = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(point)
        point
    }
}
