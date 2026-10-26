package com.udm.ezspy.ui.tree

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import com.udm.ezspy.R
import com.udm.ezspy.data.NodeSnapshot
import com.udm.ezspy.databinding.ItemNodeTreeBinding
import com.udm.ezspy.util.dp
import kotlin.math.min

class NodeTreeAdapter(
    private val onNodeClick: (Int) -> Unit,
) : RecyclerView.Adapter<NodeTreeAdapter.NodeViewHolder>() {

    private var nodes: List<NodeSnapshot> = emptyList()
    private var selectedIndex: Int = -1

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nodes: List<NodeSnapshot>, selectedIndex: Int) {
        this.nodes = nodes
        this.selectedIndex = selectedIndex
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = nodes.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemNodeTreeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val node = nodes[position]
        val selected = position == selectedIndex
        val context = holder.itemView.context

        val effectiveDepth = min(node.depth, TreeMetrics.MAX_INDENT_LEVELS)
        val guideBackground = TreeItemBackground(context, effectiveDepth, selected)
        val rippleMask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(12f).toFloat()
            setColor(Color.WHITE)
        }
        holder.binding.root.background = RippleDrawable(
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_accent_ripple)),
            guideBackground,
            rippleMask,
        )

        val indent = context.dp(
            (TreeMetrics.BASE_INDENT_DP + effectiveDepth * TreeMetrics.INDENT_STEP_DP).toFloat()
        )
        holder.binding.root.updatePaddingRelative(start = indent)
        holder.binding.root.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) onNodeClick(adapterPosition)
        }

        holder.binding.title.text = node.displayTitle
        holder.binding.subtitle.text = node.secondaryLabel
        holder.binding.rootBadge.isVisible = node.depth == 0 && position == 0
        holder.binding.dot.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (selected) R.color.color_accent else R.color.color_text_secondary,
            )
        )

        holder.binding.root.contentDescription = buildString {
            append("第 ${node.depth + 1} 层 ")
            append(node.displayTitle)
            append("，")
            append(node.secondaryLabel)
            if (selected) append("，已选中")
        }
    }

    inner class NodeViewHolder(
        val binding: ItemNodeTreeBinding,
    ) : RecyclerView.ViewHolder(binding.root)

}