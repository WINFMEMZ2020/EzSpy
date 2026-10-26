package com.udm.ezspy.ui.properties

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.udm.ezspy.R
import com.udm.ezspy.data.NodeSnapshot
import com.udm.ezspy.data.PropertyRow
import com.udm.ezspy.data.PropertyValueKind
import com.udm.ezspy.databinding.ItemPropertyHeaderBinding
import com.udm.ezspy.databinding.ItemPropertyRowBinding
import com.udm.ezspy.databinding.ItemPropertySectionBinding

class PropertiesAdapter(
    private val onCopy: (PropertyRow) -> Unit,
    private val onGetText: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Item {
        data class Header(val node: NodeSnapshot) : Item
        data class Section(val title: String) : Item
        data class Row(val row: PropertyRow) : Item
    }

    private val items = ArrayList<Item>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(node: NodeSnapshot?) {
        items.clear()
        if (node != null) {
            items.add(Item.Header(node))
            node.propertyGroups.forEach { group ->
                if (group.rows.isNotEmpty()) {
                    items.add(Item.Section(group.title))
                    group.rows.forEach { items.add(Item.Row(it)) }
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> TYPE_HEADER
        is Item.Section -> TYPE_SECTION
        is Item.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemPropertyHeaderBinding.inflate(inflater, parent, false)
            )
            TYPE_SECTION -> SectionViewHolder(
                ItemPropertySectionBinding.inflate(inflater, parent, false)
            )
            else -> RowViewHolder(
                ItemPropertyRowBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderViewHolder).bind(item.node)
            is Item.Section -> (holder as SectionViewHolder).bind(item.title)
            is Item.Row -> (holder as RowViewHolder).bind(item.row)
        }
    }

    private inner class HeaderViewHolder(
        private val binding: ItemPropertyHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: NodeSnapshot) {
            binding.title.text = node.displayTitle
            binding.subtitle.text = buildString {
                append(node.className ?: node.displayTitle)
                val packageName = node.packageName
                if (!packageName.isNullOrBlank()) {
                    append('\n')
                    node.appLabel?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                    append(packageName)
                }
            }
        }
    }

    private inner class SectionViewHolder(
        private val binding: ItemPropertySectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(title: String) {
            binding.sectionTitle.text = title
        }
    }

    private inner class RowViewHolder(
        private val binding: ItemPropertyRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: PropertyRow) {
            val context = binding.root.context
            binding.label.text = row.label
            binding.value.text = row.value

            val valueColor = when (row.kind) {
                PropertyValueKind.TRUE -> R.color.color_accent
                PropertyValueKind.FALSE -> R.color.color_text_tertiary
                PropertyValueKind.MUTED -> R.color.color_text_secondary
                PropertyValueKind.NORMAL -> R.color.color_text_primary
            }
            binding.value.setTextColor(ContextCompat.getColor(context, valueColor))

            // Only the text row gets the "get text from subtree" action.
            val isTextRow = row.label == context.getString(R.string.property_label_text)
            binding.actionButton.isVisible = isTextRow
            binding.actionButton.setOnClickListener(
                if (isTextRow) {
                    { _ -> onGetText() }
                } else {
                    null
                }
            )
            if (isTextRow) {
                binding.actionButton.contentDescription = context.getString(R.string.get_text_button)
            }

            binding.row.setOnClickListener { onCopy(row) }
            binding.row.contentDescription = buildString {
                append(row.label)
                append("，")
                append(row.value)
                append("，点击复制")
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SECTION = 1
        const val TYPE_ROW = 2
    }
}
