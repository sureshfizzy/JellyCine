package com.jellycine.ui.screens.dashboard.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource
import com.jellycine.ui.components.common.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun topbar(
    title: String,
    onBackPressed: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.cd_back_button)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
fun SettingsSection(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        accentColor = accentColor,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = accentSwitchColors(accentColor).copy(
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    disabledCheckedTrackColor = accentColor.copy(alpha = 0.45f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        }
    )
}

@Composable
fun ClickableSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        isDestructive = isDestructive,
        accentColor = accentColor,
        onClick = if (enabled) onClick else null,
        trailing = {
            if (enabled) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}


@Composable
fun BaseSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null && enabled) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = when {
                        isDestructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        else -> accentColor.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isDestructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else -> accentColor
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isDestructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueSliderSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Int,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    stepSize: Int = 1,
    onValueChanged: (Int) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    valueLabel: @Composable (Int) -> String,
    defaultLabel: @Composable (Int) -> String
) {
    val safeMin = minValue
    val safeMax = maxValue.coerceAtLeast(minValue + 1)
    val safeStepSize = stepSize.coerceAtLeast(1)
    val valueRange = safeMin..safeMax
    val sliderSteps = (((safeMax - safeMin) / safeStepSize) - 1).coerceAtLeast(0)
    val safeValue = value.coerceIn(valueRange.first, valueRange.last)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Text(
                text = valueLabel(safeValue),
                style = MaterialTheme.typography.titleSmall,
                color = accentColor
            )
        }

        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = safeValue.toFloat(),
            onValueChange = { changed ->
                val steppedValue = (
                    ((changed - safeMin) / safeStepSize).roundToInt() * safeStepSize + safeMin
                ).coerceIn(valueRange.first, valueRange.last)
                onValueChanged(steppedValue)
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = sliderSteps,
            accentColor = accentColor,
            inactiveTrackColor = accentColor.copy(alpha = 0.25f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valueLabel(valueRange.first),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = defaultLabel(defaultValue),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                modifier = Modifier.clickable {
                    onValueChanged(defaultValue.coerceIn(valueRange.first, valueRange.last))
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = valueLabel(valueRange.last),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PercentageSliderSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Int,
    defaultValue: Int,
    minValue: Int = 0,
    maxValue: Int = 50,
    stepSize: Int = 5,
    onValueChanged: (Int) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    ValueSliderSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        value = value,
        defaultValue = defaultValue,
        minValue = minValue,
        maxValue = maxValue,
        stepSize = stepSize,
        onValueChanged = onValueChanged,
        accentColor = accentColor,
        valueLabel = { currentValue ->
            stringResource(Res.string.player_settings_percent_value, currentValue)
        },
        defaultLabel = { currentValue ->
            stringResource(Res.string.player_settings_default_percent, currentValue)
        }
    )
}


@Composable
fun SelectionDialogSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selectedValue: String,
    options: List<SelectionOption>,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    var showDialog by remember { mutableStateOf(false) }

    BaseSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        accentColor = accentColor,
        onClick = { showDialog = true },
        trailing = {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    )

    if (showDialog) {
        AmoledSelectionDialog(
            title = title,
            options = options,
            selectedValue = selectedValue,
            accentColor = accentColor,
            onOptionSelected = { value ->
                onOptionSelected(value)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun ConnectionStatusBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.24f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun accentSwitchColors(accentColor: Color) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = accentColor,
    checkedBorderColor = accentColor,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable {
            if (!isLoading) onClick()
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (isDestructive)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    else
                        accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            trailing != null -> {
                trailing()
            }
            onClick != null -> {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
