package dev.cinestream.jellycine

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.database.Server
import dev.cinestream.jellycine.Adapters.ServerGridAdapter
import dev.cinestream.jellycine.Adapters.ViewListAdapter
import org.jellyfin.sdk.model.api.BaseItemDto

@BindingAdapter("servers")
fun bindServers(recyclerView: RecyclerView, data: List<Server>?) {
    val adapter = recyclerView.adapter as ServerGridAdapter
    adapter.submitList(data)
}

@BindingAdapter("views")
fun bindViews(recyclerView: RecyclerView, data: List<BaseItemDto>?) {
    val adapter = recyclerView.adapter as ViewListAdapter
    adapter.submitList(data)
}