package com.fileleader.ui.clean

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fileleader.R
import com.fileleader.data.model.JunkFile
import com.fileleader.data.model.JunkType
import com.fileleader.util.FileUtils

private sealed class JunkListItem {
    data class Header(val type: JunkType, val count: Int, val totalSize: Long, val allChecked: Boolean) : JunkListItem()
    data class Item(val file: JunkFile) : JunkListItem()
}

class JunkGroupAdapter(
    private val onGroupSelect: (JunkType, Boolean) -> Unit,
    private val onItemToggle: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<JunkListItem>()
    private val expandedTypes = mutableSetOf<JunkType>()

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_ITEM   = 1
    }

    fun submitGroups(grouped: Map<JunkType, List<JunkFile>>) {
        items.clear()
        grouped.entries.sortedByDescending { it.value.sumOf { f -> f.size } }.forEach { (type, files) ->
            val allChecked = files.isNotEmpty() && files.all { it.isSelected }
            items.add(JunkListItem.Header(type, files.size, files.sumOf { it.size }, allChecked))
            if (type in expandedTypes) {
                files.forEach { items.add(JunkListItem.Item(it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is JunkListItem.Header -> VIEW_HEADER
        is JunkListItem.Item -> VIEW_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HEADER -> HeaderVH(inflater.inflate(R.layout.item_junk_header, parent, false))
            else        -> ItemVH(inflater.inflate(R.layout.item_junk_file, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is JunkListItem.Header -> (holder as HeaderVH).bind(item)
            is JunkListItem.Item   -> (holder as ItemVH).bind(item.file)
        }
    }

    override fun getItemCount() = items.size

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvEmoji: TextView  = view.findViewById(R.id.tvTypeEmoji)
        private val tvType: TextView   = view.findViewById(R.id.tvTypeName)
        private val tvInfo: TextView   = view.findViewById(R.id.tvTypeInfo)
        private val cbGroup: CheckBox  = view.findViewById(R.id.cbGroup)
        private val tvExpand: TextView = view.findViewById(R.id.tvExpand)

        fun bind(header: JunkListItem.Header) {
            tvEmoji.text = header.type.emoji
            tvType.text  = header.type.label
            tvInfo.text  = "${header.count} 项 · ${FileUtils.formatSize(header.totalSize)}"
            cbGroup.setOnCheckedChangeListener(null)
            cbGroup.isChecked = header.allChecked
            cbGroup.setOnCheckedChangeListener { _, checked -> onGroupSelect(header.type, checked) }

            val expanded = header.type in expandedTypes
            tvExpand.text = if (expanded) "▲" else "▼"

            itemView.setOnClickListener {
                if (header.type in expandedTypes) expandedTypes.remove(header.type)
                else expandedTypes.add(header.type)
                // rebuild list
                (itemView.context as? android.app.Activity)?.runOnUiThread { notifyDataSetChanged() }
            }
        }
    }

    inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvFileName)
        private val tvSize: TextView = view.findViewById(R.id.tvFileSize)
        private val cb: CheckBox     = view.findViewById(R.id.cbFile)

        fun bind(file: JunkFile) {
            tvName.text = file.name
            tvSize.text = FileUtils.formatSize(file.size)
            cb.setOnCheckedChangeListener(null)
            cb.isChecked = file.isSelected
            cb.setOnCheckedChangeListener { _, _ -> onItemToggle(file.path) }
            itemView.setOnClickListener { onItemToggle(file.path) }
        }
    }
}
