package dev.cinestream.jellycine

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.database.Server
import dev.cinestream.jellycine.Adapters.ServerGridAdapter

@BindingAdapter("listData")
fun bindRecyclerView(recyclerView: RecyclerView, data: List<Server>?) {
    val adapter = recyclerView.adapter as ServerGridAdapter
    adapter.submitList(data)
}