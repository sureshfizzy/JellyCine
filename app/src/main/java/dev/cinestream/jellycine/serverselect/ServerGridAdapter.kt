package dev.cinestream.jellycine.serverselect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.database.Server
import dev.cinestream.jellycine.databinding.ServerItemBinding

class ServerGridAdapter (val onClickListener: OnClickListener) :
    ListAdapter<Server, ServerGridAdapter.ServerViewHolder>(DiffCallback) {
    class ServerViewHolder(private var binding: ServerItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(server: Server) {
            binding.server = server
            binding.executePendingBindings()
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Server>() {
        override fun areItemsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ServerGridAdapter.ServerViewHolder {
        return ServerViewHolder(ServerItemBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: ServerGridAdapter.ServerViewHolder, position: Int) {
        val server = getItem(position)
        holder.itemView.setOnClickListener {
            onClickListener.onClick(server)
        }
        holder.bind(server)
    }

    class OnClickListener(val clickListener: (server: Server) -> Unit) {
        fun onClick(server: Server) = clickListener(server)
    }
}