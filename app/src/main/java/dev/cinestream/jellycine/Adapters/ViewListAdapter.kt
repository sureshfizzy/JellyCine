package dev.cinestream.jellycine.Adapters


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.databinding.ViewItemBinding
import dev.cinestream.jellycine.models.View
import dev.cinestream.jellycine.models.ViewItem // Added for the lambda type

class ViewListAdapter : ListAdapter<View, ViewListAdapter.ViewViewHolder>(DiffCallback) {

    var onItemInInnerListClicked: ((ViewItem) -> Unit)? = null

    class ViewViewHolder(private var binding: ViewItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(view: View, onItemInInnerListClicked: ((ViewItem) -> Unit)?) {
            binding.view = view
            val itemsAdapter = ViewItemListAdapter()
            itemsAdapter.onItemClick = onItemInInnerListClicked
            binding.itemsRecyclerView.adapter = itemsAdapter
            binding.executePendingBindings()
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<View>() {
        override fun areItemsTheSame(oldItem: View, newItem: View): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: View, newItem: View): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewViewHolder {
        return ViewViewHolder(ViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewViewHolder, position: Int) {
        val view = getItem(position)
        holder.bind(view, onItemInInnerListClicked)
    }
}