package dev.cinestream.jellycine.Adapters


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.databinding.ViewItemBinding
import dev.cinestream.jellycine.models.View

class ViewListAdapter : ListAdapter<View, ViewListAdapter.ViewViewHolder>(DiffCallback) {
    class ViewViewHolder(private var binding: ViewItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(view: View) {
            binding.view = view
            binding.itemsRecyclerView.adapter = ViewItemListAdapter()
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
        holder.bind(view)
    }
}