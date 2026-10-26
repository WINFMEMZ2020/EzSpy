package com.udm.ezspy.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.udm.ezspy.data.NodeSnapshot
import com.udm.ezspy.data.SpyRepository

class SpyViewModel : ViewModel() {

    data class UiState(
        val nodes: List<NodeSnapshot> = emptyList(),
        val selectedIndex: Int = -1,
        val sessionId: Long = 0L,
        val targetPackage: String? = null,
        val targetLabel: String? = null,
        val windowTitle: String? = null,
        val truncated: Boolean = false,
    ) {
        val selectedNode: NodeSnapshot? get() = nodes.getOrNull(selectedIndex)
        val hasData: Boolean get() = nodes.isNotEmpty()
        val nodeCount: Int get() = nodes.size
    }

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private var lastAppliedSession = 0L

    /**
     * Applies a new result exactly once. Returns true when the state changed so
     * the activity can decide which tab to land on.
     */
    fun applyResult(result: SpyRepository.Result): Boolean {
        if (result.sessionId <= lastAppliedSession) return false
        lastAppliedSession = result.sessionId

        val capture = result.capture
        val nodes = capture?.nodes.orEmpty()
        val selected = if (nodes.isEmpty()) {
            -1
        } else {
            (capture?.pickedIndex ?: 0).coerceIn(0, nodes.lastIndex)
        }

        _state.value = UiState(
            nodes = nodes,
            selectedIndex = selected,
            sessionId = result.sessionId,
            targetPackage = capture?.targetPackage,
            targetLabel = capture?.targetLabel,
            windowTitle = capture?.windowTitle,
            truncated = capture?.truncated ?: false,
        )
        return true
    }

    fun select(index: Int) {
        val current = _state.value ?: return
        if (index !in current.nodes.indices) return
        if (current.selectedIndex == index) return
        _state.value = current.copy(selectedIndex = index)
    }

    /** Called when a new floating grab starts, so stale data is never shown. */
    fun beginNewGrab() {
        _state.value = UiState()
    }
}
