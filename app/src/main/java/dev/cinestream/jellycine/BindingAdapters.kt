package dev.cinestream.jellycine

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.database.Server
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import dev.cinestream.jellycine.Adapters.ServerGridAdapter
import dev.cinestream.jellycine.Adapters.ViewListAdapter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import dev.cinestream.jellycine.Adapters.ViewItemListAdapter
import dev.cinestream.jellycine.models.View
import dev.cinestream.jellycine.models.ViewItem

@BindingAdapter("servers")
fun bindServers(recyclerView: RecyclerView, data: List<Server>?) {
    val adapter = recyclerView.adapter as ServerGridAdapter
    adapter.submitList(data)
}

@BindingAdapter("views")
fun bindViews(recyclerView: RecyclerView, data: List<View>?) {
    val adapter = recyclerView.adapter as ViewListAdapter
    adapter.submitList(data)

}

@BindingAdapter("items")
fun bindItems(recyclerView: RecyclerView, data: List<ViewItem>?) {
    val adapter = recyclerView.adapter as ViewItemListAdapter
    adapter.submitList(data)
}

@BindingAdapter("itemImage")
fun bindItemImage(imageView: ImageView, item: ViewItem) {
    Glide
        .with(imageView.context)
        .load(item.primaryImageUrl)
        .transition(DrawableTransitionOptions.withCrossFade())
        .placeholder(R.color.neutral_800)
        .into(imageView)
}