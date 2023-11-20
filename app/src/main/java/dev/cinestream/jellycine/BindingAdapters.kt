package dev.cinestream.jellycine

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.cinestream.jellycine.database.Server
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import dev.cinestream.jellycine.Adapters.ServerGridAdapter
import dev.cinestream.jellycine.Adapters.ViewListAdapter
import dev.cinestream.jellycine.Adapters.CollectionListAdapter
import android.widget.ImageView
import com.bumptech.glide.Glide
import dev.cinestream.jellycine.Adapters.ViewItemListAdapter
import dev.cinestream.jellycine.models.View
import dev.cinestream.jellycine.models.ViewItem
import dev.cinestream.jellycine.api.JellyfinApi
import org.jellyfin.sdk.model.api.BaseItemDto

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
    imageView.contentDescription = "${item.name} poster"
}

@BindingAdapter("collections")
fun bindCollections(recyclerView: RecyclerView, data: List<BaseItemDto>?) {
    val adapter = recyclerView.adapter as CollectionListAdapter
    adapter.submitList(data)
}

@BindingAdapter("collectionImage")
fun bindCollectionImage(imageView: ImageView, item: BaseItemDto) {
    val jellyfinApi = JellyfinApi.getInstance(imageView.context.applicationContext, "")

    Glide
        .with(imageView.context)
        .load(jellyfinApi.api.baseUrl.plus("/items/${item.id}/Images/Primary"))
        .transition(DrawableTransitionOptions.withCrossFade())
        .placeholder(R.color.neutral_800)
        .into(imageView)

    imageView.contentDescription = "${item.name} image"
}