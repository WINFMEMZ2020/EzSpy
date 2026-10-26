package com.udm.ezspy.ui.properties

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.udm.ezspy.R
import com.udm.ezspy.data.NodeSnapshot
import com.udm.ezspy.data.PrivacySettings
import com.udm.ezspy.data.PropertyRow
import com.udm.ezspy.databinding.DialogNodeTextBinding
import com.udm.ezspy.databinding.FragmentPropertiesBinding
import com.udm.ezspy.ui.SpyViewModel

class PropertiesFragment : Fragment(R.layout.fragment_properties) {

    private var _binding: FragmentPropertiesBinding? = null
    private val binding: FragmentPropertiesBinding get() = _binding!!

    private lateinit var adapter: PropertiesAdapter
    private var lastNode: NodeSnapshot? = null
    private var lastShowSensitive: Boolean? = null
    private var currentState: SpyViewModel.UiState? = null

    private val viewModel: SpyViewModel by lazy {
        ViewModelProvider(requireActivity())[SpyViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPropertiesBinding.bind(view)

        adapter = PropertiesAdapter(
            onCopy = { row, value -> copyProperty(row, value) },
            onGetText = { showNodeTextDialog() },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = null

        viewModel.state.observe(viewLifecycleOwner) { state ->
            currentState = state
            render(state.selectedNode)
        }
        PrivacySettings.showSensitiveContent.observe(viewLifecycleOwner) {
            render(currentState?.selectedNode)
        }
    }

    private fun render(node: NodeSnapshot?) {
        val showSensitive = PrivacySettings.showSensitiveContent.value == true
        binding.recycler.isVisible = node != null
        binding.emptyState.isVisible = node == null

        if (node == null) {
            adapter.submit(null, showSensitive)
            lastNode = null
            lastShowSensitive = showSensitive
            return
        }

        if (node == lastNode && showSensitive == lastShowSensitive) return
        lastNode = node
        lastShowSensitive = showSensitive
        adapter.submit(node, showSensitive)
        binding.recycler.post { binding.recycler.scrollToPosition(0) }
    }

    private fun copyProperty(row: PropertyRow, value: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(row.label, value))
        Snackbar.make(
            binding.root,
            getString(R.string.copied, row.label),
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    /**
     * Collects the text of the selected node and every descendant, one line
     * per control, in the same pre-order as the captured tree. Controls without
     * text are skipped; if nothing has text, the dialog opens blank.
     */
    private fun showNodeTextDialog() {
        val state = currentState ?: return
        val start = state.selectedIndex
        if (start !in state.nodes.indices) return

        val depth = state.nodes[start].depth
        var end = start + 1
        while (end < state.nodes.size && state.nodes[end].depth > depth) {
            end++
        }

        val lines = ArrayList<String>()
        val showSensitive = PrivacySettings.showSensitiveContent.value == true
        for (index in start until end) {
            val node = state.nodes[index]
            val line = node.textForDisplay(showSensitive)?.trim()?.takeIf { it.isNotEmpty() }
                ?: node.contentDescriptionForDisplay(showSensitive)?.trim()?.takeIf { it.isNotEmpty() }
            if (line != null) lines.add(line)
        }
        val text = lines.joinToString("\n")

        val dialogBinding = DialogNodeTextBinding.inflate(layoutInflater)
        dialogBinding.textInput.setText(text)
        dialogBinding.textInput.setSelection(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.get_text_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.get_text_copy) { _, _ ->
                copyGeneratedText(dialogBinding.textInput.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.get_text_close, null)
            .show()
    }

    private fun copyGeneratedText(text: String) {
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.get_text_dialog_title), text)
        )
        Snackbar.make(
            binding.root,
            getString(R.string.copied, getString(R.string.get_text_dialog_title)),
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    override fun onDestroyView() {
        binding.recycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
