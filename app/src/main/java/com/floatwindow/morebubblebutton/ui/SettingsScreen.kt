package com.floatwindow.morebubblebutton.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatwindow.morebubblebutton.BuildConfig
import com.floatwindow.morebubblebutton.ModuleSettings
import com.floatwindow.morebubblebutton.MoreBubbleHookModule
import com.floatwindow.morebubblebutton.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val SettingsCardShape = RoundedCornerShape(28.dp)

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var menuEnabled by remember { mutableStateOf(ModuleSettings.isMenuEnabled(ctx)) }
    var actionBarEnabled by remember { mutableStateOf(ModuleSettings.isActionBarEnabled(ctx)) }
    var systemUiBubbleEnabled by remember { mutableStateOf(ModuleSettings.isSystemUiBubbleEnabled(ctx)) }
    var openMode by remember { mutableIntStateOf(ModuleSettings.getOpenMode(ctx)) }
    var popupPresentation by remember { mutableIntStateOf(ModuleSettings.getPopupPresentation(ctx)) }
    var swipeHandleLength by remember {
        mutableFloatStateOf(ModuleSettings.getSwipeHandleLength(ctx).toFloat())
    }
    var swipeHandleThickness by remember {
        mutableFloatStateOf(ModuleSettings.getSwipeHandleThickness(ctx).toFloat())
    }
    var swipeHandleBottomMargin by remember {
        mutableFloatStateOf(ModuleSettings.getSwipeHandleBottomMargin(ctx).toFloat())
    }
    var positionMode by remember { mutableIntStateOf(ModuleSettings.getPositionMode(ctx)) }
    var sliderX by remember { mutableFloatStateOf(ModuleSettings.getPosX(ctx).toFloat()) }
    var sliderY by remember { mutableFloatStateOf(ModuleSettings.getPosY(ctx).toFloat()) }
    var rootCheckKey by remember { mutableIntStateOf(0) }
    var rootAccess by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(rootCheckKey) {
        rootAccess = null
        rootAccess = withContext(Dispatchers.IO) { hasRootAccess() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeaderCard() }

        item { SectionHeading(Icons.Default.Apps, R.string.section_recents) }
        item {
            SettingsCard {
                PreferenceSwitchRow(
                    icon = Icons.Default.Apps,
                    title = stringResource(R.string.feature_menu_title),
                    summary = stringResource(R.string.feature_menu_summary),
                    checked = menuEnabled,
                    onCheckedChange = {
                        menuEnabled = it
                        ModuleSettings.setMenuEnabled(ctx, it)
                    }
                )
                PreferenceDivider()
                PreferenceSwitchRow(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.feature_action_bar_title),
                    summary = stringResource(R.string.feature_action_bar_summary),
                    checked = actionBarEnabled,
                    onCheckedChange = {
                        actionBarEnabled = it
                        ModuleSettings.setActionBarEnabled(ctx, it)
                    }
                )
            }
        }

        item { SectionHeading(Icons.Default.Notifications, R.string.section_notifications) }
        item {
            SettingsCard {
                PreferenceSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.feature_system_ui_title),
                    summary = stringResource(R.string.feature_system_ui_summary),
                    checked = systemUiBubbleEnabled,
                    onCheckedChange = {
                        systemUiBubbleEnabled = it
                        ModuleSettings.setSystemUiBubbleEnabled(ctx, it)
                    }
                )
                PreferenceDivider()
                ChoicePreferenceRow(
                    title = stringResource(R.string.open_mode_title),
                    summary = stringResource(R.string.open_mode_summary),
                    selected = openMode,
                    labels = listOf(
                        stringResource(R.string.open_mode_bubble),
                        stringResource(R.string.open_mode_freeform)
                    ),
                    onSelected = {
                        openMode = it
                        ModuleSettings.setOpenMode(ctx, it)
                    }
                )
                PreferenceDivider()
                ChoicePreferenceRow(
                    title = stringResource(R.string.popup_presentation_title),
                    summary = stringResource(R.string.popup_presentation_summary),
                    selected = popupPresentation,
                    labels = listOf(
                        stringResource(R.string.popup_presentation_button),
                        stringResource(R.string.popup_presentation_handle)
                    ),
                    onSelected = {
                        popupPresentation = it
                        ModuleSettings.setPopupPresentation(ctx, it)
                    }
                )
                if (popupPresentation == ModuleSettings.POPUP_PRESENTATION_SWIPE_HANDLE) {
                    PreferenceDivider()
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        FineTuneSlider(
                            label = stringResource(R.string.swipe_handle_length),
                            hint = stringResource(R.string.swipe_handle_length_hint),
                            value = swipeHandleLength,
                            valueText = stringResource(
                                R.string.swipe_handle_value,
                                stringResource(R.string.swipe_handle_length),
                                swipeHandleLength.toInt()
                            ),
                            valueRange = 32f..96f,
                            steps = 63,
                            onValueChange = { swipeHandleLength = it },
                            onCommit = {
                                ModuleSettings.setSwipeHandleLength(ctx, swipeHandleLength.toInt())
                            },
                            onStep = { delta ->
                                swipeHandleLength = (swipeHandleLength + delta).coerceIn(32f, 96f)
                                ModuleSettings.setSwipeHandleLength(ctx, swipeHandleLength.toInt())
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FineTuneSlider(
                            label = stringResource(R.string.swipe_handle_thickness),
                            hint = stringResource(R.string.swipe_handle_thickness_hint),
                            value = swipeHandleThickness,
                            valueText = stringResource(
                                R.string.swipe_handle_value,
                                stringResource(R.string.swipe_handle_thickness),
                                swipeHandleThickness.toInt()
                            ),
                            valueRange = 3f..14f,
                            steps = 10,
                            onValueChange = { swipeHandleThickness = it },
                            onCommit = {
                                ModuleSettings.setSwipeHandleThickness(ctx, swipeHandleThickness.toInt())
                            },
                            onStep = { delta ->
                                swipeHandleThickness = (swipeHandleThickness + delta).coerceIn(3f, 14f)
                                ModuleSettings.setSwipeHandleThickness(ctx, swipeHandleThickness.toInt())
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FineTuneSlider(
                            label = stringResource(R.string.swipe_handle_bottom_margin),
                            hint = stringResource(R.string.swipe_handle_bottom_margin_hint),
                            value = swipeHandleBottomMargin,
                            valueText = stringResource(
                                R.string.swipe_handle_value,
                                stringResource(R.string.swipe_handle_bottom_margin),
                                swipeHandleBottomMargin.toInt()
                            ),
                            valueRange = 0f..32f,
                            steps = 31,
                            onValueChange = { swipeHandleBottomMargin = it },
                            onCommit = {
                                ModuleSettings.setSwipeHandleBottomMargin(ctx, swipeHandleBottomMargin.toInt())
                            },
                            onStep = { delta ->
                                swipeHandleBottomMargin = (swipeHandleBottomMargin + delta).coerceIn(0f, 32f)
                                ModuleSettings.setSwipeHandleBottomMargin(ctx, swipeHandleBottomMargin.toInt())
                            }
                        )
                    }
                }
            }
        }

        item { SectionHeading(Icons.Default.Tune, R.string.section_position) }
        item {
            SettingsCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.position_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = positionMode == 0,
                            onClick = {
                                positionMode = 0
                                ModuleSettings.setPositionMode(ctx, 0)
                                applyPosition(ctx)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = stringResource(R.string.position_mode_follow),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        SegmentedButton(
                            selected = positionMode == 1,
                            onClick = {
                                positionMode = 1
                                ModuleSettings.setPositionMode(ctx, 1)
                                applyPosition(ctx)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = stringResource(R.string.position_mode_second),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }

                    if (positionMode == 1) {
                        Spacer(modifier = Modifier.height(18.dp))
                        FineTuneSlider(
                            label = stringResource(R.string.position_x),
                            hint = stringResource(R.string.position_x_hint),
                            value = sliderX,
                            valueText = stringResource(
                                R.string.position_value,
                                stringResource(R.string.position_x), sliderX.toInt()
                            ),
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { sliderX = it },
                            onCommit = {
                                ModuleSettings.setPosX(ctx, sliderX.toInt())
                                applyPosition(ctx)
                            },
                            onStep = { delta ->
                                sliderX = (sliderX + delta).coerceIn(0f, 100f)
                                ModuleSettings.setPosX(ctx, sliderX.toInt())
                                applyPosition(ctx)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FineTuneSlider(
                            label = stringResource(R.string.position_y),
                            hint = stringResource(R.string.position_y_hint),
                            value = sliderY,
                            valueText = stringResource(
                                R.string.position_value,
                                stringResource(R.string.position_y), sliderY.toInt()
                            ),
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { sliderY = it },
                            onCommit = {
                                ModuleSettings.setPosY(ctx, sliderY.toInt())
                                applyPosition(ctx)
                            },
                            onStep = { delta ->
                                sliderY = (sliderY + delta).coerceIn(0f, 100f)
                                ModuleSettings.setPosY(ctx, sliderY.toInt())
                                applyPosition(ctx)
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                sliderX = 50f
                                sliderY = 50f
                                ModuleSettings.setPosX(ctx, 50)
                                ModuleSettings.setPosY(ctx, 50)
                                applyPosition(ctx)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.reset_position))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.position_mode_follow_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { SectionHeading(Icons.Default.Refresh, R.string.section_actions) }
        item {
            SettingsCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.restart_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.restart_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    RootAccessStatus(
                        hasRootAccess = rootAccess,
                        onOpenManager = { openRootManager(ctx) },
                        onCheckAgain = { rootCheckKey++ }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { restartSystemUi(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = rootAccess == true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.restart_button))
                    }
                }
            }
        }

        item { SectionHeading(Icons.Default.Info, R.string.section_about) }
        item {
            SettingsCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    AboutRow(
                        title = stringResource(R.string.app_name),
                        value = stringResource(R.string.version_value, BuildConfig.VERSION_NAME)
                    )
                    AboutRow(
                        title = stringResource(R.string.compatibility_value),
                        value = stringResource(R.string.settings_language_auto)
                    )
                    Text(
                        text = stringResource(R.string.about_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RootAccessStatus(
    hasRootAccess: Boolean?,
    onOpenManager: () -> Unit,
    onCheckAgain: () -> Unit
) {
    val title = stringResource(R.string.root_status_title)
    val summary = when (hasRootAccess) {
        true -> stringResource(R.string.root_status_granted)
        false -> stringResource(R.string.root_status_missing)
        null -> stringResource(R.string.root_status_checking)
    }
    val summaryColor = if (hasRootAccess == false) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = summaryColor
        )
        if (hasRootAccess == false) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenManager,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.root_open_manager), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCheckAgain,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.root_check_again), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = stringResource(R.string.app_icon_description),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_auto),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(icon: ImageVector, titleRes: Int) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(content = content)
    }
}

@Composable
private fun PreferenceSwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreferenceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun ChoicePreferenceRow(
    title: String,
    summary: String,
    selected: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = { onSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun FineTuneSlider(
    label: String,
    hint: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onStep: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = { onStep(-1f) }) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.decrease_value, label)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onCommit,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        FilledTonalIconButton(onClick = { onStep(1f) }) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.increase_value, label)
            )
        }
    }
}

@Composable
private fun AboutRow(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun applyPosition(ctx: android.content.Context) {
    try {
        MoreBubbleHookModule.applyPositionFromSettings(ctx)
    } catch (_: Throwable) {
        // The hook process may not be loaded yet; the saved value is still applied on next load.
    }
}

private val rootManagerPackages = listOf(
    "com.rifsxd.ksunext",
    "me.weishu.kernelsu",
    "com.topjohnwu.magisk"
)

private fun hasRootAccess(): Boolean {
    return try {
        val result = runRootCommand("id")
        !result.timedOut && result.exitCode == 0 && result.output.contains("uid=0")
    } catch (_: Throwable) {
        false
    }
}

private fun openRootManager(ctx: Context) {
    val managerIntent = rootManagerPackages
        .asSequence()
        .mapNotNull { packageName -> ctx.packageManager.getLaunchIntentForPackage(packageName) }
        .firstOrNull()

    if (managerIntent == null) {
        Toast.makeText(ctx, R.string.root_manager_missing, Toast.LENGTH_LONG).show()
        return
    }

    managerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(managerIntent)
}

private data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false
)

private fun restartSystemUi(ctx: android.content.Context) {
    Thread {
        val result = try {
            val rootCheck = runRootCommand("id")
            if (rootCheck.timedOut || rootCheck.exitCode != 0 || !rootCheck.output.contains("uid=0")) {
                false
            } else {
                // EvolutionX uses NexusLauncher; keep Launcher3 as a fallback for other ROM builds.
                listOf(
                    "com.google.android.apps.nexuslauncher",
                    "com.android.launcher3",
                    "com.android.systemui"
                ).forEach { packageName ->
                    // An absent package must not make the whole restart operation fail.
                    runRootCommand("killall $packageName 2>/dev/null || true")
                }
                true
            }
        } catch (_: Throwable) {
            false
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                ctx,
                if (result) R.string.restart_success else R.string.restart_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }.start()
}

private fun runRootCommand(command: String): RootCommandResult {
    val process = ProcessBuilder("su", "-c", command)
        .redirectErrorStream(true)
        .start()
    val finished = process.waitFor(5, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        return RootCommandResult(-1, "", timedOut = true)
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return RootCommandResult(process.exitValue(), output)
}
