package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jellycine.app.BuildConfig
import com.jellycine.app.R

private const val GithubUrl = "https://github.com/sureshfizzy/JellyCine"
private const val PrivacyUrl = "https://github.com/sureshfizzy/JellyCine/blob/main/PRIVACY"
private const val LicenseUrl = "https://github.com/sureshfizzy/JellyCine/blob/main/LICENSE"
private const val PlayStoreUrl = "https://play.google.com/store/apps/details?id=com.jellycine.app"
private const val BuyMeACoffeeUrl = "https://www.buymeacoffee.com/Sureshfizzy"
private const val PatreonUrl = "https://www.patreon.com/c/sureshs/membership"
private const val ContactEmail = "Sureshfizzy0503@gmail.com"
private const val GithubIconUrl = "https://cdn.simpleicons.org/github/white?viewbox=auto"
private const val BuyMeACoffeeIconUrl = "https://cdn.simpleicons.org/buymeacoffee?viewbox=auto"
private const val PatreonIconUrl = "https://cdn.simpleicons.org/patreon/white?viewbox=auto"

private val AboutCardColor = Color(0xFF0B0E12)
private val AboutBorderColor = Color.White.copy(alpha = 0.08f)
private val AboutSecondaryText = Color.White.copy(alpha = 0.70f)
private val AboutAccent = Color(0xFF5AA9FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                AboutHeader(
                    onGithubClick = { openUrl(context, GithubUrl) },
                    onCoffeeClick = { openUrl(context, BuyMeACoffeeUrl) },
                    onPatreonClick = { openUrl(context, PatreonUrl) }
                )
            }

            item { AboutSectionLabel(stringResource(R.string.about_section_project)) }
            item {
                AboutSectionCard {
                    AboutActionRow(
                        icon = Icons.Rounded.Policy,
                        title = stringResource(R.string.about_privacy_policy),
                        subtitle = stringResource(R.string.about_privacy_policy_subtitle),
                        onClick = { openUrl(context, PrivacyUrl) }
                    )
                    HorizontalDivider(color = AboutBorderColor)
                    AboutActionRow(
                        icon = Icons.Rounded.Gavel,
                        title = stringResource(R.string.about_open_source_license),
                        subtitle = stringResource(R.string.about_open_source_license_subtitle),
                        onClick = { openUrl(context, LicenseUrl) }
                    )
                }
            }

            item { AboutSectionLabel(stringResource(R.string.about_section_connect)) }
            item {
                AboutSectionCard {
                    AboutActionRow(
                        icon = Icons.Rounded.StarRate,
                        title = stringResource(R.string.about_rate_app),
                        subtitle = stringResource(R.string.about_rate_app_subtitle),
                        onClick = { openUrl(context, PlayStoreUrl) }
                    )
                    HorizontalDivider(color = AboutBorderColor)
                    AboutActionRow(
                        icon = Icons.Rounded.Email,
                        title = stringResource(R.string.about_contact_developer),
                        subtitle = stringResource(R.string.about_contact_developer_subtitle),
                        onClick = { composeEmail(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(
    onGithubClick: () -> Unit,
    onCoffeeClick: () -> Unit,
    onPatreonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.jellycine_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(112.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            color = AboutAccent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, AboutAccent.copy(alpha = 0.28f))
        ) {
            Text(
                text = stringResource(R.string.about_version_chip, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandIconButton(
                contentDescription = stringResource(R.string.about_source_code),
                imageUrl = GithubIconUrl,
                onClick = onGithubClick
            )
            BrandIconButton(
                contentDescription = stringResource(R.string.about_buy_me_a_coffee),
                imageUrl = BuyMeACoffeeIconUrl,
                onClick = onCoffeeClick
            )
            BrandIconButton(
                contentDescription = stringResource(R.string.about_patreon),
                imageUrl = PatreonIconUrl,
                onClick = onPatreonClick
            )
        }
    }
}

@Composable
private fun BrandIconButton(
    contentDescription: String,
    imageUrl: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun AboutSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White.copy(alpha = 0.88f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun AboutSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AboutCardColor),
        border = BorderStroke(1.dp, AboutBorderColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            color = AboutAccent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AboutAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AboutSecondaryText
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = title,
            tint = Color.White.copy(alpha = 0.42f)
        )
    }
}

private fun openUrl(context: Context, url: String) {
    launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun composeEmail(context: Context) {
    launchIntent(
        context,
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$ContactEmail")
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_feedback_subject))
        }
    )
}

private fun launchIntent(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}