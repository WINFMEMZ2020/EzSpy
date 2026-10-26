package com.udm.ezspy.ui.grabber

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.udm.ezspy.MainActivity
import com.udm.ezspy.R
import com.udm.ezspy.databinding.FragmentGrabberBinding
import com.udm.ezspy.service.EzSpyAccessibilityService
import com.udm.ezspy.util.PermissionUtils

class GrabberFragment : Fragment(R.layout.fragment_grabber) {

    private var _binding: FragmentGrabberBinding? = null
    private val binding: FragmentGrabberBinding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGrabberBinding.bind(view)
        binding.grabButton.setOnClickListener { onGrabClicked() }
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) updatePermissionStatus()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
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
        if (!service.startGrab()) {
            showToast(R.string.grab_start_failed_overlay)
            return
        }

        (requireActivity() as MainActivity).onGrabStarted()
        requireActivity().moveTaskToBack(true)
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
}
