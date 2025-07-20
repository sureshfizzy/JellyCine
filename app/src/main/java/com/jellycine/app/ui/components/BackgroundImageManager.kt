package com.jellycine.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun rememberBackgroundImageUrl(
    refreshIntervalMs: Long = 10000L
): String {
    var backgroundUrl by remember { mutableStateOf(CinematicBackgrounds.getRandomImage()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(refreshIntervalMs)
            backgroundUrl = CinematicBackgrounds.getRandomImage()
        }
    }

    return backgroundUrl
}

object CinematicBackgrounds {
    private val images = listOf(
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/5P8SmMzSNYikXpxil6BYzJ16611.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/p1F51Lvj3sMopG948F5HsBbl43C.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/faXT8V80JRhnArTAeYXz0Eutpv9.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/iQFcwSGbZXMkeyKrxbPnwnRo5fl.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/cinER0ESG0eJ49kXlExM0MEWGxW.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/suaEOtk1N1sgg2MTM7oZd2cfVp3.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/7RyHsO4yDXtBv1zUU3mTpHeQ0d5.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/qAKvUu2FSaDlnqznY4VOp5PmjIF.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/bOGkgRGdhrBYJSLpXaxhXVstddV.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/9BBTo63ANSmhC4e6r62OJFuK2GL.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/lmZFxXgJE3vgrciwuDib0N8CfQo.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/6CoRTJTmijhBLJTUNoVSUNxZMEI.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/vBZ0qvaRxqEhZwl6LWmruJqWE8Z.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/8Y43POKjjKDGI9MH89NW0NAzzp8.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/rAiYTfKGqDCRIIqo664sY9XZIvQ.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/6MKr3KgOLmzOP6MSuZERO41Lpkt.jpg",
        "https://image.tmdb.org/t/p/w1920_and_h1080_multi_faces/hziiv14OpD73u9gAak4XDDfBKa2.jpg"
    )

    fun getRandomImage(): String {
        return images[Random.nextInt(images.size)]
    }

    fun getAllImages(): List<String> = images

    fun getImagesByCategory(category: String): List<String> {
        return when (category.lowercase()) {
            "scifi", "action" -> images.take(8)
            "superhero", "marvel", "dc" -> images.drop(2).take(6)
            "epic", "fantasy" -> images.drop(5).take(5)
            "thriller", "drama" -> images.drop(10).take(5)
            "classic", "legendary" -> images.drop(15).take(5)
            else -> images
        }
    }
}
