package com.jellycine.app.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.manager.AuthStateManager
import kotlinx.coroutines.launch

// Sample movie data for demonstration
data class Movie(
    val id: String,
    val title: String,
    val year: String,
    val posterUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val authStateManager = remember { AuthStateManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    // Sample movies for demonstration
    val sampleMovies = remember {
        listOf(
            Movie("1", "Spider-Man: No Way Home", "2021"),
            Movie("2", "Joker", "2019"),
            Movie("3", "The Batman", "2022"),
            Movie("4", "Final Destination", "2000"),
            Movie("5", "Avengers: Endgame", "2019"),
            Movie("6", "Dune", "2021"),
            Movie("7", "Top Gun: Maverick", "2022"),
            Movie("8", "Black Widow", "2021"),
            Movie("9", "Fast & Furious 9", "2021"),
            Movie("10", "No Time to Die", "2021"),
            Movie("11", "Eternals", "2021"),
            Movie("12", "Venom: Let There Be Carnage", "2021")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        ) {
            // Header with logout button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Explore now",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            authStateManager.logout()
                            onLogout()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExitToApp,
                        contentDescription = "Logout",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "to experience the benefits",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Movie Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sampleMovies) { movie ->
                    MovieCard(movie = movie)
                }
            }
        }
    }
}

@Composable
private fun MovieCard(movie: Movie) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Poster placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎬",
                    fontSize = 32.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Movie info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = movie.year,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
