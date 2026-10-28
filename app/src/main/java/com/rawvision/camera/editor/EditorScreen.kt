@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawvision.camera.R
import com.rawvision.camera.data.model.EditParams
import com.rawvision.camera.ui.components.ConfirmDialog
import com.rawvision.camera.ui.components.LabeledSlider
import com.rawvision.camera.ui.components.RvTopBar
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.DeviceSaver
import com.rawvision.camera.util.Formatters
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val photo by vm.photo.collectAsStateWithLifecycle()
    val params by vm.params.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val original by vm.original.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    var showOriginal by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val leave = { vm.onLeave(); onBack() }
    BackHandler { leave() }
    DisposableEffect(Unit) { onDispose { } }

    val savedText = stringResource(R.string.editor_saved)
    val exportedText = stringResource(R.string.editor_exported)
    val savedToDeviceText = stringResource(R.string.saved_to_device)
    val shareTitle = stringResource(R.string.action_share)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is EditorEvent.Saved -> snackbar.showSnackbar(savedText)
                is EditorEvent.Error -> snackbar.showSnackbar(e.message)
                is EditorEvent.Exported -> when (e.action) {
                    ExportAction.SHARE -> {
                        context.startActivity(Intent.createChooser(Exporter.shareIntent(context, e.file), shareTitle))
                    }
                    ExportAction.SAVE_TO_DEVICE -> scope.launch {
                        val msg = runCatching { DeviceSaver.save(context, e.file, "image/jpeg") }
                            .fold({ savedToDeviceText }, { it.message ?: exportedText })
                        snackbar.showSnackbar(msg)
                    }
                }
            }
        }
    }
    val saveToDevice = rememberSaveToDevice { vm.export(ExportAction.SAVE_TO_DEVICE) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RvTopBar(
                title = "",
                onBack = leave,
                backDescription = stringResource(R.string.action_back),
                actions = {
                    IconButton(onClick = vm::undo, enabled = canUndo) { LucideIcon(Lucide.Undo, stringResource(R.string.action_undo)) }
                    IconButton(onClick = vm::redo, enabled = canRedo) { LucideIcon(Lucide.Redo, stringResource(R.string.action_redo)) }
                    IconButton(onClick = { showHistory = true }) { LucideIcon(Lucide.RotateCcw, stringResource(R.string.editor_history)) }
                    IconButton(onClick = saveToDevice, enabled = !exporting && !busy) {
                        LucideIcon(Lucide.Download, stringResource(R.string.action_save_device))
                    }
                    IconButton(onClick = { vm.export(ExportAction.SHARE) }, enabled = !exporting && !busy) {
                        if (exporting) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        else LucideIcon(Lucide.Share, stringResource(R.string.action_share))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { LucideIcon(Lucide.MoreVertical, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_save_version)) },
                                leadingIcon = { LucideIcon(Lucide.Save, null, size = 18.dp) },
                                onClick = { menu = false; vm.saveSnapshot() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_reset_all)) },
                                leadingIcon = { LucideIcon(Lucide.Eraser, null, size = 18.dp) },
                                onClick = { menu = false; confirmReset = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ---- Image ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            showOriginal = true
                            tryAwaitRelease()
                            showOriginal = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = if (showOriginal) original else (preview ?: original)
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (busy) CircularProgressIndicator(color = Color.White)
                if (showOriginal) {
                    Text(
                        stringResource(R.string.editor_original),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // ---- Tabs ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { vm.selectTab(t) },
                        label = { Text(stringResource(tabLabel(t))) },
                        leadingIcon = { LucideIcon(tabIcon(t), null, size = 16.dp) },
                    )
                }
            }

            // ---- Sliders ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                when (tab) {
                    EditorTab.LIGHT -> LightTab(params, vm)
                    EditorTab.COLOR -> ColorTab(params, vm)
                    EditorTab.DETAIL -> DetailTab(params, vm)
                    EditorTab.EFFECTS -> EffectsTab(params, vm)
                    EditorTab.GEOMETRY -> GeometryTab(params, vm)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.editor_reset_all),
            message = stringResource(R.string.editor_reset_confirm),
            confirmText = stringResource(R.string.action_reset),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { confirmReset = false; vm.resetAll() },
            onDismiss = { confirmReset = false },
            destructive = true,
        )
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.editor_history), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.editor_history_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(Lucide.FileImage, null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.editor_original), modifier = Modifier.weight(1f))
                    TextButton(onClick = { showHistory = false; vm.resetAll() }) { Text(stringResource(R.string.action_restore)) }
                }
                if (history.isEmpty()) {
                    Text(stringResource(R.string.editor_history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                history.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(Lucide.Clock, null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Formatters.dateTime(v.createdAt), style = MaterialTheme.typography.bodyMedium)
                            Text(summarize(v.params), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showHistory = false; vm.restore(v) }) { Text(stringResource(R.string.action_restore)) }
                    }
                }
            }
        }
    }
}

/**
 * Returns a click handler that runs [action], asking for the legacy storage
 * permission first on Android 9 (newer versions need none).
 */
@Composable
fun rememberSaveToDevice(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) action() }
    return {
        if (DeviceSaver.needsPermission(context)) launcher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else action()
    }
}

private fun summarize(p: EditParams): String {
    if (p.isIdentity) return "—"
    val parts = mutableListOf<String>()
    fun add(name: String, v: Float) { if (v != 0f) parts += String.format(Locale.US, "%s %+.0f", name, v) }
    if (p.exposure != 0f) parts += String.format(Locale.US, "EV %+.1f", p.exposure)
    add("C", p.contrast); add("H", p.highlights); add("S", p.shadows); add("T", p.temperature); add("Sat", p.saturation)
    if (p.rotation != 0) parts += "${p.rotation}°"
    if (p.hasCrop) parts += "crop"
    return parts.joinToString(" · ").ifEmpty { "…" }
}

private fun tabLabel(t: EditorTab): Int = when (t) {
    EditorTab.LIGHT -> R.string.editor_tab_light
    EditorTab.COLOR -> R.string.editor_tab_color
    EditorTab.DETAIL -> R.string.editor_tab_detail
    EditorTab.EFFECTS -> R.string.editor_tab_effects
    EditorTab.GEOMETRY -> R.string.editor_tab_geometry
}

private fun tabIcon(t: EditorTab): Int = when (t) {
    EditorTab.LIGHT -> Lucide.Sun
    EditorTab.COLOR -> Lucide.Palette
    EditorTab.DETAIL -> Lucide.Sparkles
    EditorTab.EFFECTS -> Lucide.Blend
    EditorTab.GEOMETRY -> Lucide.Crop
}

@Composable
private fun EditSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    icon: Int,
    vm: EditorViewModel,
    valueText: String = String.format(Locale.US, "%+.0f", value),
    default: Float = 0f,
    set: (EditParams, Float) -> EditParams,
) {
    LabeledSlider(
        label = label,
        value = value,
        onValueChange = { v -> vm.update { set(it, v) } },
        onValueChangeFinished = vm::commit,
        valueRange = range,
        valueText = valueText,
        icon = icon,
        onReset = { vm.apply { set(it, default) } },
    )
}

@Composable
private fun LightTab(p: EditParams, vm: EditorViewModel) {
    EditSlider(stringResource(R.string.edit_exposure), p.exposure, -3f..3f, Lucide.Sun, vm, String.format(Locale.US, "%+.2f", p.exposure)) { e, v -> e.copy(exposure = v) }
    EditSlider(stringResource(R.string.edit_contrast), p.contrast, -100f..100f, Lucide.Contrast, vm) { e, v -> e.copy(contrast = v) }
    EditSlider(stringResource(R.string.edit_highlights), p.highlights, -100f..100f, Lucide.SunMedium, vm) { e, v -> e.copy(highlights = v) }
    EditSlider(stringResource(R.string.edit_shadows), p.shadows, -100f..100f, Lucide.SunDim, vm) { e, v -> e.copy(shadows = v) }
    EditSlider(stringResource(R.string.edit_whites), p.whites, -100f..100f, Lucide.CircleDashed, vm) { e, v -> e.copy(whites = v) }
    EditSlider(stringResource(R.string.edit_blacks), p.blacks, -100f..100f, Lucide.Circle, vm) { e, v -> e.copy(blacks = v) }
}

@Composable
private fun ColorTab(p: EditParams, vm: EditorViewModel) {
    EditSlider(stringResource(R.string.edit_temperature), p.temperature, -100f..100f, Lucide.Thermometer, vm) { e, v -> e.copy(temperature = v) }
    EditSlider(stringResource(R.string.edit_tint), p.tint, -100f..100f, Lucide.Droplet, vm) { e, v -> e.copy(tint = v) }
    EditSlider(stringResource(R.string.edit_saturation), p.saturation, -100f..100f, Lucide.Droplets, vm) { e, v -> e.copy(saturation = v) }
    EditSlider(stringResource(R.string.edit_vibrance), p.vibrance, -100f..100f, Lucide.Palette, vm) { e, v -> e.copy(vibrance = v) }
}

@Composable
private fun DetailTab(p: EditParams, vm: EditorViewModel) {
    EditSlider(stringResource(R.string.edit_sharpness), p.sharpness, 0f..100f, Lucide.Sparkles, vm, String.format(Locale.US, "%.0f", p.sharpness)) { e, v -> e.copy(sharpness = v) }
    EditSlider(stringResource(R.string.edit_clarity), p.clarity, -100f..100f, Lucide.Focus, vm) { e, v -> e.copy(clarity = v) }
    EditSlider(stringResource(R.string.edit_dehaze), p.dehaze, -100f..100f, Lucide.Cloud, vm) { e, v -> e.copy(dehaze = v) }
}

@Composable
private fun EffectsTab(p: EditParams, vm: EditorViewModel) {
    EditSlider(stringResource(R.string.edit_vignette), p.vignette, -100f..100f, Lucide.CircleDot, vm) { e, v -> e.copy(vignette = v) }
}

@Composable
private fun GeometryTab(p: EditParams, vm: EditorViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { vm.apply { it.copy(rotation = (it.rotation + 270) % 360) } }) { LucideIcon(Lucide.RotateCcw, stringResource(R.string.edit_rotate_left)) }
        IconButton(onClick = { vm.apply { it.copy(rotation = (it.rotation + 90) % 360) } }) { LucideIcon(Lucide.RotateCw, stringResource(R.string.edit_rotate_right)) }
        IconButton(onClick = { vm.apply { it.copy(flipHorizontal = !it.flipHorizontal) } }) { LucideIcon(Lucide.ArrowLeftRight, stringResource(R.string.edit_flip_h)) }
        IconButton(onClick = { vm.apply { it.copy(flipVertical = !it.flipVertical) } }) { LucideIcon(Lucide.ArrowUpDown, stringResource(R.string.edit_flip_v)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { vm.apply { it.copy(rotation = 0, straighten = 0f, flipHorizontal = false, flipVertical = false, cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f) } }) {
            Text(stringResource(R.string.action_reset))
        }
    }
    EditSlider(stringResource(R.string.edit_straighten), p.straighten, -45f..45f, Lucide.Ruler, vm, String.format(Locale.US, "%+.1f°", p.straighten)) { e, v -> e.copy(straighten = (v * 10f).toInt() / 10f) }
    Text(stringResource(R.string.edit_crop), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val presets = listOf<Pair<String, Float?>>(
            stringResource(R.string.edit_crop_free) to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 1.5f, "16:9" to 16f / 9f, "3:4" to 3f / 4f, "9:16" to 9f / 16f,
        )
        presets.forEach { (label, aspect) ->
            FilterChip(
                selected = false,
                onClick = { vm.setCropAspect(aspect) },
                label = { Text(label) },
            )
        }
    }
}
