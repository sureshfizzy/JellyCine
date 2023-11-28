package dev.cinestream.jellycine.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.databinding.GenreItemBinding
import org.jellyfin.sdk.model.api.*

class GenreListAdapter :
    ListAdapter<BaseItemDto, GenreListAdapter.ViewViewHolder>(DiffCallback) {
    class ViewViewHolder(private var binding: GenreItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(Genre: BaseItemDto) {
            binding.genres = Genre
            binding.executePendingBindings()
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BaseItemDto>() {
        override fun areItemsTheSame(oldItem: BaseItemDto, newItem: BaseItemDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BaseItemDto, newItem: BaseItemDto): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewViewHolder {
        return ViewViewHolder(
            GenreItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewViewHolder, position: Int) {
        val Genre = getItem(position)
        holder.bind(Genre)
    }
}