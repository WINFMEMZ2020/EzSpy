package com.udm.ezspy.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * In-process bridge between the accessibility service (which owns the floating
 * overlay) and the main activity (which shows the result).
 *
 * A result with a null capture means "return to the main UI with nothing
 * selected", which is different from not having a result at all.
 */
object SpyRepository {

    data class Result(
        val capture: Capture?,
        val sessionId: Long,
    )

    private val _result = MutableLiveData<Result?>(null)
    val result: LiveData<Result?> = _result

    private var sessionCounter = 0L

    fun publish(capture: Capture?) {
        sessionCounter += 1
        _result.postValue(Result(capture, sessionCounter))
    }

    fun clear() {
        _result.postValue(null)
    }
}
