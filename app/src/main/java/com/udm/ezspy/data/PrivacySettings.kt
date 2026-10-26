package com.udm.ezspy.data

import androidx.lifecycle.MutableLiveData

/**
 * In-memory-only privacy switches shown on the grabber page.
 *
 * Nothing here is persisted: a fresh app process starts with the safe defaults
 * again. A configuration change recreates the activity but keeps this object,
 * so the switches survive rotation within the same process.
 */
object PrivacySettings {

    /** When false (default), password field content is rendered as bullets. */
    val showSensitiveContent = MutableLiveData(false)

    /** When true, the 3-minute session cap is not applied. */
    val unlimitedSession = MutableLiveData(false)

    /** When true, MainActivity clears FLAG_SECURE so screenshots are allowed. */
    val disableSecureScreen = MutableLiveData(false)

    fun reset() {
        showSensitiveContent.value = false
        unlimitedSession.value = false
        disableSecureScreen.value = false
    }
}
