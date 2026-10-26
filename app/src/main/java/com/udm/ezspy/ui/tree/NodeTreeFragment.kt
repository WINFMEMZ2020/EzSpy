package com.udm.ezspy.ui.tree

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.udm.ezspy.MainActivity
import com.udm.ezspy.R
import com.udm.ezspy.databinding.FragmentNodeTreeBinding
import com.udm.ezspy.ui.SpyViewModel
import kotlin.math.max
import kotlin.math.min

class NodeTreeFragment : Fragment(R.layout.fragment_node_tree) {

    private var _binding: FragmentNodeTreeBinding? = null
    private val binding: FragmentNodeTreeBinding get() = _binding!!

    private lateinit var adapter: NodeTreeAdapter
    private var lastScrolledSession = -1L

    private val viewModel: SpyViewModel by lazy {
        ViewModelProvider(requireActivity())[SpyViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNodeTreeBinding.bind(view)

        adapter = NodeTreeAdapter { position ->
            viewModel.select(position)
            (requireActivity() as MainActivity).openPropertiesTab()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.itemAnimator = null

        viewModel.state.observe(viewLifecycleOwner) { state -> render(state) }
    }

    private fun render(state: SpyViewModel.UiState) {
        binding.treeContent.isVisible = state.hasData
        binding.emptyState.isVisible = !state.hasData

        if (!state.hasData) {
            adapter.submit(emptyList(), -1)
            lastScrolledSession = -1L
            return
        }

        val target = state.targetPackage
            ?: state.targetLabel
            ?: getString(R.string.tree_target_unknown)
        val truncated = if (state.truncated) getString(R.string.tree_truncated_suffix) else ""
        binding.treeMeta.text = getString(
            R.string.tree_meta_format,
            target,
            state.nodeCount,
            truncated,
        )

        applyTreeWidth(state)
        adapter.submit(state.nodes, state.selectedIndex)

        if (state.sessionId != lastScrolledSession) {
            lastScrolledSession = state.sessionId
            val index = state.selectedIndex.coerceIn(0, state.nodes.lastIndex)
            val depth = min(state.selectedNode?.depth ?: 0, TreeMetrics.MAX_INDENT_LEVELS)
            val indentPx = (
                (TreeMetrics.BASE_INDENT_DP + depth * TreeMetrics.INDENT_STEP_DP) *
                    resources.displayMetrics.density
                ).toInt()

            binding.recycler.postDelayed({
                if (_binding == null) return@postDelayed
                // Keep the picked node visible together with its ancestors
                // above and its siblings below.
                val layoutManager = binding.recycler.layoutManager as? LinearLayoutManager
                if (layoutManager != null && binding.recycler.height > 0) {
                    layoutManager.scrollToPositionWithOffset(index, binding.recycler.height / 3)
                } else {
                    binding.recycler.scrollToPosition(index)
                }
                // Deep trees are panned: put the picked node's bullet near the
                // left edge instead of squeezing the title into a narrow column.
                binding.treeHorizontalScroll.scrollTo(indentPx, 0)
            }, 90L)
        }
    }

    /**
     * The list is wider than the screen so deep nodes keep a readable title
     * area. Horizontal gestures pan the list instead of switching tabs.
     */
    private fun applyTreeWidth(state: SpyViewModel.UiState) {
        val density = resources.displayMetrics.density
        val viewport = binding.root.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val maxDepth = state.nodes.maxOfOrNull {
            min(it.depth, TreeMetrics.MAX_INDENT_LEVELS)
        } ?: 0
        val indentPx = (
            (TreeMetrics.BASE_INDENT_DP + maxDepth * TreeMetrics.INDENT_STEP_DP) * density
            ).toInt()
        val titleArea = max(viewport, (TreeMetrics.TITLE_AREA_DP * density).toInt())
        val contentWidth = max(viewport, indentPx + titleArea + (16 * density).toInt())

        binding.recycler.contentWidth = contentWidth
    }

    override fun onDestroyView() {
        binding.recycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
