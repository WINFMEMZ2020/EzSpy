package com.udm.ezspy.ui.grabber

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.udm.ezspy.MainActivity
import com.udm.ezspy.R
import com.udm.ezspy.data.PrivacySettings
import com.udm.ezspy.data.SESSION_TIMEOUT_MS
import com.udm.ezspy.data.SessionEndReason
import com.udm.ezspy.data.SessionState
import com.udm.ezspy.data.SpyRepository
import com.udm.ezspy.databinding.FragmentGrabberBinding
import com.udm.ezspy.service.EzSpyAccessibilityService
import com.udm.ezspy.util.PermissionUtils
import com.udm.ezspy.util.formatElapsed

class GrabberFragment : Fragment(R.layout.fragment_grabber) {

    private var _binding: FragmentGrabberBinding? = null
    private val binding: FragmentGrabberBinding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())

    private val sessionTicker = object : Runnable {
        override fun run() {
            if (_binding == null) return
            renderSession()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGrabberBinding.bind(view)
        binding.grabButton.setOnClickListener { onGrabClicked() }
        binding.stopButton.setOnClickListener { stopSession() }
        setupPrivacyControls()
        SpyRepository.session.observe(viewLifecycleOwner) { renderSession() }
        updatePermissionStatus()
        renderSession()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            updatePermissionStatus()
            handler.removeCallbacks(sessionTicker)
            handler.post(sessionTicker)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(sessionTicker)
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(sessionTicker)
        _binding = null
        super.onDestroyView()
    }

    private fun setupPrivacyControls() {
        binding.showSensitiveCheck.isChecked =
            PrivacySettings.showSensitiveContent.value == true
        binding.showSensitiveCheck.setOnCheckedChangeListener { _, checked ->
            PrivacySettings.showSensitiveContent.value = checked
        }
        PrivacySettings.showSensitiveContent.observe(viewLifecycleOwner) { checked ->
            if (binding.showSensitiveCheck.isChecked != checked) {
                binding.showSensitiveCheck.isChecked = checked
            }
        }

        binding.unlimitedSessionCheck.isChecked =
            PrivacySettings.unlimitedSession.value == true
        binding.unlimitedSessionCheck.setOnCheckedChangeListener { _, checked ->
            PrivacySettings.unlimitedSession.value = checked
        }
        PrivacySettings.unlimitedSession.observe(viewLifecycleOwner) { checked ->
            if (binding.unlimitedSessionCheck.isChecked != checked) {
                binding.unlimitedSessionCheck.isChecked = checked
            }
        }

        binding.disableSecureCheck.isChecked =
            PrivacySettings.disableSecureScreen.value == true
        binding.disableSecureCheck.setOnCheckedChangeListener { _, checked ->
            PrivacySettings.disableSecureScreen.value = checked
        }
        PrivacySettings.disableSecureScreen.observe(viewLifecycleOwner) { checked ->
            if (binding.disableSecureCheck.isChecked != checked) {
                binding.disableSecureCheck.isChecked = checked
            }
        }
    }

    private fun renderSession() {
        if (_binding == null) return
        val state = SpyRepository.session.value ?: SessionState()
        val now = SystemClock.elapsedRealtime()
        val status = when {
            state.active -> {
                val limit = if (state.unlimited) {
                    getString(R.string.session_unlimited)
                } else {
                    formatElapsed(SESSION_TIMEOUT_MS)
                }
                getString(
                    R.string.session_running_format,
                    formatElapsed(state.elapsedMs(now)),
                    limit,
                )
            }

            state.startedAtElapsed > 0L -> {
                val reason = when (state.endReason) {
                    SessionEndReason.TIMEOUT -> R.string.session_timeout
                    SessionEndReason.RETURNED -> R.string.session_returned
                    else -> R.string.session_stopped
                }
                getString(
                    R.string.session_ended_format,
                    getString(reason),
                    formatElapsed(state.elapsedMs(now)),
                )
            }

            else -> getString(R.string.session_idle)
        }

        binding.sessionStatusText.text = status
        binding.stopButton.isVisible = state.active
        binding.grabButton.isEnabled = !state.active
        binding.unlimitedSessionCheck.isEnabled = !state.active
    }

    private fun updatePermissionStatus() {
        val context = requireContext()
        val accessibilityReady = PermissionUtils.isAccessibilityServiceEnabled(context)
        val overlayReady = PermissionUtils.canDrawOverlays(context)

        val readyColor = ContextCompat.getColor(context, R.color.color_status_ready)
        val missingColor = ContextCompat.getColor(context, R.color.color_status_missing)
        val labelColor = ContextCompat.getColor(context, R.color.color_text_secondary)

        val text = SpannableStringBuilder()
        appendStatus(
            text,
            ready = accessibilityReady,
            label = getString(R.string.grab_status_accessibility),
            readyColor = readyColor,
            missingColor = missingColor,
            labelColor = labelColor,
        )
        text.append("    ")
        appendStatus(
            text,
            ready = overlayReady,
            label = getString(R.string.grab_status_overlay),
            readyColor = readyColor,
            missingColor = missingColor,
            labelColor = labelColor,
        )
        binding.statusText.text = text

        val description = if (accessibilityReady && overlayReady) {
            getString(R.string.grab_ready_content_description)
        } else {
            text.toString()
        }
        binding.statusText.contentDescription = description
    }

    private fun appendStatus(
        builder: SpannableStringBuilder,
        ready: Boolean,
        label: String,
        readyColor: Int,
        missingColor: Int,
        labelColor: Int,
    ) {
        val statusColor = if (ready) readyColor else missingColor

        val dotStart = builder.length
        builder.append("● ")
        builder.setSpan(
            ForegroundColorSpan(statusColor),
            dotStart,
            dotStart + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        val labelStart = builder.length
        builder.append(label)
        builder.setSpan(
            ForegroundColorSpan(labelColor),
            labelStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        builder.append(' ')
        val suffixStart = builder.length
        builder.append(
            getString(if (ready) R.string.grab_status_ready_suffix else R.string.grab_status_missing_suffix)
        )
        builder.setSpan(
            ForegroundColorSpan(statusColor),
            suffixStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    private fun onGrabClicked() {
        val context = requireContext()
        if (SpyRepository.session.value?.active == true) return
        if (!PermissionUtils.isAccessibilityServiceEnabled(context)) {
            showAccessibilityDialog()
            return
        }
        if (!PermissionUtils.canDrawOverlays(context)) {
            showOverlayDialog()
            return
        }

        val service = EzSpyAccessibilityService.instance
        if (service == null) {
            showToast(R.string.grab_start_failed_service)
            return
        }
        val unlimited = PrivacySettings.unlimitedSession.value == true
        if (!service.startGrab(unlimited)) {
            showToast(R.string.grab_start_failed_overlay)
            return
        }

        (requireActivity() as MainActivity).onGrabStarted()
        requireActivity().moveTaskToBack(true)
    }

    private fun stopSession() {
        val service = EzSpyAccessibilityService.instance
        if (service != null) {
            service.stopSessionAndDiscard(SessionEndReason.STOPPED)
        } else {
            SpyRepository.clear()
            SpyRepository.endSession(SessionEndReason.STOPPED)
        }
    }

    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_accessibility_title)
            .setMessage(R.string.dialog_accessibility_message)
            .setPositiveButton(R.string.dialog_go_settings) { _, _ ->
                if (!PermissionUtils.openAccessibilitySettings(requireContext())) {
                    showToast(R.string.settings_open_failed)
                }
            }
            .setNegativeButton(R.string.dialog_later, null)
            .show()
    }

    private fun showOverlayDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_overlay_title)
            .setMessage(R.string.dialog_overlay_message)
            .setPositiveButton(R.string.dialog_go_settings) { _, _ ->
                if (!PermissionUtils.openOverlaySettings(requireContext())) {
                    showToast(R.string.settings_open_failed)
                }
            }
            .setNegativeButton(R.string.dialog_later, null)
            .show()
    }

    private fun showToast(resId: Int) {
        val context: Context = requireContext()
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TICK_MS = 1000L
    }
}
