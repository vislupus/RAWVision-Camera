@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawvision.camera.R
import com.rawvision.camera.data.AppContainer
import com.rawvision.camera.data.model.AccentColor
import com.rawvision.camera.data.model.AppSettings
import com.rawvision.camera.data.model.CaptureFormat
import com.rawvision.camera.data.model.FontScale
import com.rawvision.camera.data.model.GridMode
import com.rawvision.camera.data.model.ThemeMode
import com.rawvision.camera.ui.components.RowDivider
import com.rawvision.camera.ui.components.RvTopBar
import com.rawvision.camera.ui.components.SectionCard
import com.rawvision.camera.ui.components.SectionHeader
import com.rawvision.camera.ui.components.SegmentedChoice
import com.rawvision.camera.ui.components.SettingRow
import com.rawvision.camera.ui.components.SwitchRow
import com.rawvision.camera.ui.theme.Dimens
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenDiagnostics: () -> Unit = {}) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { container.settings.update(transform) }
    }

    var usedBytes by remember { mutableLongStateOf(0L) }
    var freeBytes by remember { mutableLongStateOf(0L) }
    var photoCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            usedBytes = container.storage.usedBytes()
            freeBytes = container.storage.freeBytes()
            photoCount = container.database.photos.count()
        }
    }

    Scaffold(
        topBar = { RvTopBar(title = stringResource(R.string.nav_settings), onBack = onBack, backDescription = stringResource(R.string.action_back)) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Appearance ----
            SectionHeader(stringResource(R.string.settings_section_appearance))
            SectionCard {
                Column(Modifier.padding(horizontal = Dimens.md.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = ThemeMode.entries,
                        selected = settings.themeMode,
                        onSelected = { m -> update { it.copy(themeMode = m) } },
                        label = { stringResource(themeLabel(it)) },
                        icon = { themeIcon(it) },
                    )
                }
                RowDivider()
                SwitchRow(Lucide.Moon, stringResource(R.string.settings_amoled), stringResource(R.string.settings_amoled_hint), settings.amoledBlack) { v -> update { it.copy(amoledBlack = v) } }
                RowDivider()
                Column(Modifier.padding(horizontal = Dimens.md.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(Lucide.Palette, null, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        AccentColor.entries.forEach { accent ->
                            val selected = settings.accent == accent && !settings.dynamicColor
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(accent.seed))
                                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .clickable { update { it.copy(accent = accent, dynamicColor = false) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) LucideIcon(Lucide.Check, null, size = 16.dp, tint = Color.White)
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RowDivider()
                    SwitchRow(Lucide.Wand, stringResource(R.string.settings_dynamic_color), stringResource(R.string.settings_dynamic_color_hint), settings.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
                }
                RowDivider()
                Column(Modifier.padding(horizontal = Dimens.md.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(Lucide.FontSize, null, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = FontScale.entries,
                        selected = settings.fontScale,
                        onSelected = { f -> update { it.copy(fontScale = f) } },
                        label = { stringResource(fontLabel(it)) },
                    )
                }
            }

            // ---- Author ----
            SectionHeader(stringResource(R.string.settings_section_author))
            SectionCard {
                Column(Modifier.padding(Dimens.md.dp)) {
                    var author by remember(settings.authorName) { mutableStateOf(settings.authorName) }
                    var copyright by remember(settings.copyright) { mutableStateOf(settings.copyright) }
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it; update { s -> s.copy(authorName = it) } },
                        label = { Text(stringResource(R.string.settings_author)) },
                        leadingIcon = { LucideIcon(Lucide.User, null, size = 20.dp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = copyright,
                        onValueChange = { copyright = it; update { s -> s.copy(copyright = it) } },
                        label = { Text(stringResource(R.string.settings_copyright)) },
                        leadingIcon = { LucideIcon(Lucide.Copyright, null, size = 20.dp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.settings_author_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Capture ----
            SectionHeader(stringResource(R.string.settings_section_capture))
            SectionCard {
                Column(Modifier.padding(horizontal = Dimens.md.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.settings_format), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = CaptureFormat.entries,
                        selected = settings.captureFormat,
                        onSelected = { f -> update { it.copy(captureFormat = f) } },
                        label = { stringResource(formatLabel(it)) },
                    )
                }
                RowDivider()
                Column(Modifier.padding(horizontal = Dimens.md.dp, vertical = 12.dp)) {
                    Row {
                        Text(stringResource(R.string.settings_jpeg_quality), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${settings.jpegQuality}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = settings.jpegQuality.toFloat(),
                        onValueChange = { v -> update { it.copy(jpegQuality = v.toInt()) } },
                        valueRange = 60f..100f,
                    )
                }
                RowDivider()
                SwitchRow(Lucide.MapPin, stringResource(R.string.settings_location), stringResource(R.string.settings_location_hint), settings.saveLocation) { v -> update { it.copy(saveLocation = v) } }
                RowDivider()
                Column(Modifier.padding(horizontal = Dimens.md.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.settings_grid), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = GridMode.entries,
                        selected = settings.grid,
                        onSelected = { g -> update { it.copy(grid = g) } },
                        label = { stringResource(gridLabel(it)) },
                    )
                }
                RowDivider()
                SwitchRow(Lucide.Ruler, stringResource(R.string.settings_level), null, settings.showLevel) { v -> update { it.copy(showLevel = v) } }
                RowDivider()
                SwitchRow(Lucide.Histogram, stringResource(R.string.settings_histogram), stringResource(R.string.settings_histogram_hint), settings.showHistogram) { v -> update { it.copy(showHistogram = v) } }
                RowDivider()
                SwitchRow(Lucide.Activity, stringResource(R.string.settings_shutter_sound), null, settings.shutterSound) { v -> update { it.copy(shutterSound = v) } }
                RowDivider()
                SwitchRow(Lucide.Smartphone, stringResource(R.string.settings_haptics), null, settings.haptics) { v -> update { it.copy(haptics = v) } }
                RowDivider()
                SwitchRow(Lucide.Bolt, stringResource(R.string.settings_volume_shutter), null, settings.volumeShutter) { v -> update { it.copy(volumeShutter = v) } }
                RowDivider()
                SwitchRow(Lucide.Eye, stringResource(R.string.settings_keep_screen_on), null, settings.keepScreenOn) { v -> update { it.copy(keepScreenOn = v) } }
                RowDivider()
                SwitchRow(Lucide.Images, stringResource(R.string.settings_copy_gallery), stringResource(R.string.settings_copy_gallery_hint), settings.copyToGallery) { v -> update { it.copy(copyToGallery = v) } }
            }

            // ---- Storage ----
            SectionHeader(stringResource(R.string.settings_section_storage))
            SectionCard {
                SettingRow(Lucide.Folder, stringResource(R.string.settings_storage_path), container.storage.root.absolutePath)
                RowDivider()
                SettingRow(Lucide.Images, stringResource(R.string.settings_storage_photos), "$photoCount")
                RowDivider()
                SettingRow(Lucide.HardDrive, stringResource(R.string.settings_storage_used), Formatters.bytes(usedBytes) + "  ·  " + stringResource(R.string.settings_storage_free, Formatters.bytes(freeBytes)))
                RowDivider()
                SettingRow(Lucide.Database, stringResource(R.string.settings_storage_db), container.storage.databaseFile.name)
                RowDivider()
                SettingRow(Lucide.Cpu, stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_hint), onClick = onOpenDiagnostics)
            }

            // ---- About ----
            SectionHeader(stringResource(R.string.settings_section_about))
            SectionCard {
                SettingRow(Lucide.Info, stringResource(R.string.app_name), stringResource(R.string.settings_version, versionName(container)))
                RowDivider()
                SettingRow(Lucide.Shield, stringResource(R.string.settings_licenses), "Lucide (ISC) · Roboto (OFL)")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun versionName(container: AppContainer): String = runCatching {
    val pm = container.appContext.packageManager
    pm.getPackageInfo(container.appContext.packageName, 0).versionName ?: "?"
}.getOrDefault("?")

private fun themeLabel(m: ThemeMode): Int = when (m) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun themeIcon(m: ThemeMode): Int = when (m) {
    ThemeMode.SYSTEM -> Lucide.Monitor
    ThemeMode.LIGHT -> Lucide.Sun
    ThemeMode.DARK -> Lucide.Moon
}

private fun fontLabel(f: FontScale): Int = when (f) {
    FontScale.SMALL -> R.string.font_small
    FontScale.NORMAL -> R.string.font_normal
    FontScale.LARGE -> R.string.font_large
    FontScale.EXTRA_LARGE -> R.string.font_extra_large
}

private fun formatLabel(f: CaptureFormat): Int = when (f) {
    CaptureFormat.RAW_JPEG -> R.string.format_raw_jpeg
    CaptureFormat.RAW_ONLY -> R.string.format_raw
    CaptureFormat.JPEG_ONLY -> R.string.format_jpeg
}

private fun gridLabel(g: GridMode): Int = when (g) {
    GridMode.NONE -> R.string.grid_none
    GridMode.THIRDS -> R.string.grid_thirds
    GridMode.GOLDEN -> R.string.grid_golden
    GridMode.SQUARE -> R.string.grid_square
}
