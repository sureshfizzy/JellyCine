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
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.query.ImageOptions
import android.view.View
import android.widget.ProgressBar
import com.google.android.material.button.MaterialButton // Added
// ImageView is already imported

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

@BindingAdapter("itemProgress")
fun bindItemProgressBar(progressBar: ProgressBar, item: ViewItem?) {
    if (item?.playedPercentage == null || item.playedPercentage <= 1.0 || item.playedPercentage >= 99.0) {
        progressBar.visibility = View.GONE
    } else {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = item.playedPercentage.toInt()
    }
}

@BindingAdapter("itemFavorite")
fun bindItemFavoriteIcon(imageView: ImageView, item: ViewItem?) {
    if (item?.isFavorite == true) {
        imageView.visibility = View.VISIBLE
    } else {
        imageView.visibility = View.GONE
    }
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

@BindingAdapter("mediaBackdrop")
fun bindMediaBackdropImage(imageView: ImageView, item: BaseItemDto?) {
    if (item == null) {
        imageView.setImageDrawable(null) // Or a placeholder
        return
    }
    val jellyfinApi = JellyfinApi.getInstance(imageView.context.applicationContext, "") // Assuming serverAddress is not needed here or is already configured
    val imageOptions = ImageOptions(
        imageType = ImageType.BACKDROP,
        maxWidth = 1280 // Example: fetch a reasonably sized backdrop
    )
    // Check if there's an image tag for backdrop, otherwise primary might be used by SDK or it might fail.
    // The SDK's getItemImageUrl typically handles cases where a specific image type might be missing
    // and might fall back or require specific image tags.
    val imageUrl = jellyfinApi.api.getItemImageUrl(item.id, imageOptions.imageType!!, imageOptions)


    Glide.with(imageView.context)
        .load(imageUrl)
        .placeholder(R.color.md_theme_surface) // M3 theme color
        .error(R.color.md_theme_surface)       // M3 theme color
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(imageView)
    imageView.contentDescription = "${item.name} backdrop image"
}

@BindingAdapter("mediaPoster")
fun bindMediaPosterImage(imageView: ImageView, item: BaseItemDto?) {
    if (item == null) {
        imageView.setImageDrawable(null) // Or a placeholder
        return
    }
    val jellyfinApi = JellyfinApi.getInstance(imageView.context.applicationContext, "")
    val imageOptions = ImageOptions(
        imageType = ImageType.PRIMARY,
        maxWidth = 400 // Example: fetch a reasonably sized poster
    )
    val imageUrl = jellyfinApi.api.getItemImageUrl(item.id, imageOptions.imageType!!, imageOptions)

    Glide.with(imageView.context)
        .load(imageUrl)
        .placeholder(R.color.md_theme_surfaceVariant) // M3 theme color
        .error(R.color.md_theme_surfaceVariant)       // M3 theme color
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(imageView)
    imageView.contentDescription = "${item.name} poster image"
}

@BindingAdapter("favoriteState")
fun bindFavoriteState(button: MaterialButton, isFavorite: Boolean?) {
    if (isFavorite == true) {
        button.setIconResource(android.R.drawable.star_on)
    } else {
        button.setIconResource(android.R.drawable.star_off)
    }
}