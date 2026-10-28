@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawvision.camera.R
import com.rawvision.camera.data.model.GridMode
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The viewfinder screen. Dark UI regardless of theme, like every camera app. */
@Composable
fun CameraScreen(
    vm: CameraViewModel,
    volumeKeys: kotlinx.coroutines.flow.Flow<Unit>,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true
        if (hasCameraPermission) vm.onResume()
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            val perms = mutableListOf(Manifest.permission.CAMERA)
            if (settings.saveLocation) perms += Manifest.permission.ACCESS_FINE_LOCATION
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    // Tie sensors / camera to the lifecycle.
    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (hasCameraPermission) vm.onResume()
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The viewfinder is always dark: use light status-bar icons while it is visible.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    val snackbar = remember { SnackbarHostState() }
    var flashAlpha by remember { mutableStateOf(0f) }
    val savedText = stringResource(R.string.camera_saved)
    val rawFailedText = stringResource(R.string.camera_raw_failed)
    val captureFailedText = stringResource(R.string.camera_capture_failed)
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is CameraEvent.Shutter -> { flashAlpha = 1f; delay(80); flashAlpha = 0f }
                is CameraEvent.Saved ->
                    if (event.rawError != null) snackbar.showSnackbar(rawFailedText + "\n" + event.rawError, duration = androidx.compose.material3.SnackbarDuration.Long)
                    else snackbar.showSnackbar(savedText + " " + event.photo.displayName)
                is CameraEvent.Error -> snackbar.showSnackbar(captureFailedText + " " + event.message)
            }
        }
    }
    LaunchedEffect(settings.volumeShutter) {
        if (settings.volumeShutter) volumeKeys.collectLatest { vm.onShutterPressed() }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { _ ->
        if (!hasCameraPermission) {
            PermissionRequired(onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) })
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraContent(vm, onOpenGallery, onOpenSettings, onOpenPhoto)
            val shutterFlash by animateFloatAsState(flashAlpha, tween(90), label = "shutterFlash")
            if (shutterFlash > 0f) Box(Modifier.fillMaxSize().alpha(shutterFlash).background(Color.White))
        }
    }
}

@Composable
private fun PermissionRequired(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LucideIcon(Lucide.Camera, null, size = 56.dp, tint = Color.White)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.permission_camera_title), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.permission_camera_body), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
    }
}

@Composable
private fun CameraContent(
    vm: CameraViewModel,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
) {
    val controller = vm.controller
    val info by controller.info.collectAsStateWithLifecycle()
    val params by controller.params.collectAsStateWithLifecycle()
    val meter by controller.meter.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val histogram by controller.histogram.collectAsStateWithLifecycle()
    val focusPoint by controller.focusPoint.collectAsStateWithLifecycle()
    val analysis by controller.analysisActive.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val panel by vm.panel.collectAsStateWithLifecycle()
    val countdown by vm.countdown.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val lastPhoto by vm.lastPhoto.collectAsStateWithLifecycle()
    val roll by vm.level.roll.collectAsStateWithLifecycle()
    val locationFix by vm.location.location.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // ---- Viewfinder (controls are drawn on top of it) ----
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val current = info
            if (current != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInputGestures(vm, params.zoom),
                ) {
                    CameraPreview(controller, current, Modifier.fillMaxWidth())
                    GridOverlay(settings.grid, Modifier.matchParentSize())
                    if (settings.showLevel) LevelOverlay(roll, Modifier.matchParentSize())
                    FocusRing(focusPoint, meter.isFocused, meter.isFocusing, Modifier.matchParentSize())
                    if (settings.showHistogram && analysis) {
                        HistogramOverlay(histogram, Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 12.dp))
                    }
                    if (current.supportsRaw.not()) {
                        Text(
                            stringResource(R.string.camera_no_raw),
                            color = Color(0xFFFFC857),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 64.dp, end = 12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (countdown > 0) {
                        Text(
                            countdown.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center).clickable { vm.cancelTimer() },
                        )
                    }
                    when (val st = status) {
                        is CameraStatus.Error -> Text(
                            st.message,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error).padding(8.dp),
                        )
                        is CameraStatus.Opening, CameraStatus.Closed -> CircularProgressIndicator(
                            Modifier.align(Alignment.Center), color = Color.White,
                        )
                        else -> Unit
                    }
                    CameraTopBar(
                        vm = vm,
                        info = current,
                        params = params,
                        grid = settings.grid,
                        hasLocation = settings.saveLocation && locationFix != null,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.camera_none), color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.nav_settings)) }
                }
            }
        }

        // ---- Readouts + control panel ----
        ReadoutBar(
            params = params,
            meter = meter,
            info = info,
            active = panel,
            onSelect = vm::togglePanel,
        )
        AnimatedVisibility(visible = panel != ControlPanel.NONE, enter = expandVertically(), exit = shrinkVertically()) {
            val current = info
            if (current != null) {
                ControlPanelContent(panel, params, meter, current, vm)
            }
        }

        // ---- Bottom bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Last photo thumbnail
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onOpenGallery),
                contentAlignment = Alignment.Center,
            ) {
                val thumbPath = lastPhoto?.thumbPath
                if (thumbPath != null) {
                    ThumbnailImage(thumbPath, Modifier.fillMaxSize())
                } else {
                    LucideIcon(Lucide.Images, stringResource(R.string.nav_gallery), tint = Color.White, size = 22.dp)
                }
            }

            ShutterButton(saving = saving, enabled = status is CameraStatus.Previewing, onClick = vm::onShutterPressed)

            // Mode toggle
            val modeColor by animateColorAsState(
                if (params.isFullAuto) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary,
                label = "modeColor",
            )
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(modeColor)
                    .clickable {
                        if (params.isFullAuto) vm.togglePanel(ControlPanel.ISO) else vm.resetToAuto()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    params.modeLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun Modifier.pointerInputGestures(vm: CameraViewModel, zoom: Float): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { offset -> vm.focusAt(offset.x / size.width, offset.y / size.height) },
            onDoubleTap = { vm.setZoom(1f) },
        )
    }
    .pointerInput(zoom) {
        var current = zoom
        detectTransformGestures { _, _, zoomChange, _ ->
            if (zoomChange != 1f) {
                current *= zoomChange
                vm.setZoom(current)
            }
        }
    }

@Composable
private fun ShutterButton(saving: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
            .clickable(enabled = enabled && !saving, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (saving) CircularProgressIndicator(Modifier.size(30.dp), color = Color.Black, strokeWidth = 3.dp)
    }
}

@Composable
private fun CameraTopBar(
    vm: CameraViewModel,
    info: CameraInfo?,
    params: CameraParams,
    grid: GridMode,
    hasLocation: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lensMenu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val white = Color.White
        IconButton(onClick = vm::cycleFlash, enabled = info?.hasFlash != false) {
            val (icon, tint) = when (params.flash) {
                FlashMode.OFF -> Lucide.ZapOff to white.copy(alpha = 0.7f)
                FlashMode.AUTO -> Lucide.Zap to white
                FlashMode.ON -> Lucide.Zap to MaterialTheme.colorScheme.primary
                FlashMode.TORCH -> Lucide.Flashlight to MaterialTheme.colorScheme.primary
            }
            LucideIcon(icon, stringResource(R.string.camera_flash), tint = tint)
        }
        IconButton(onClick = vm::cycleTimer) {
            Box(contentAlignment = Alignment.Center) {
                LucideIcon(
                    if (params.timer == TimerMode.OFF) Lucide.TimerOff else Lucide.Timer,
                    stringResource(R.string.camera_timer),
                    tint = if (params.timer == TimerMode.OFF) white.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                )
                if (params.timer != TimerMode.OFF) {
                    Text(
                        params.timer.seconds.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 0.dp),
                    )
                }
            }
        }
        IconButton(onClick = {
            vm.setGrid(
                when (grid) {
                    GridMode.NONE -> GridMode.THIRDS
                    GridMode.THIRDS -> GridMode.GOLDEN
                    GridMode.GOLDEN -> GridMode.SQUARE
                    GridMode.SQUARE -> GridMode.NONE
                }
            )
        }) {
            LucideIcon(Lucide.Grid, stringResource(R.string.camera_grid), tint = if (grid == GridMode.NONE) white.copy(alpha = 0.7f) else white)
        }
        if (hasLocation) {
            LucideIcon(Lucide.MapPin, stringResource(R.string.camera_location_on), size = 16.dp, tint = Color(0xFF58E07A), modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.weight(1f))

        val lenses = vm.lensesForCurrentFacing()
        if (lenses.size > 1 && info != null) {
            Box {
                Text(
                    info.label,
                    color = white,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(white.copy(alpha = 0.12f))
                        .clickable { lensMenu = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                DropdownMenu(expanded = lensMenu, onDismissRequest = { lensMenu = false }) {
                    lenses.forEach { lens ->
                        DropdownMenuItem(
                            text = { Text(lens.label + if (lens.supportsRaw) "  RAW" else "") },
                            onClick = { lensMenu = false; vm.selectCamera(lens.id) },
                        )
                    }
                }
            }
        }
        IconButton(onClick = vm::switchFacing) {
            LucideIcon(Lucide.SwitchCamera, stringResource(R.string.camera_switch), tint = white)
        }
        IconButton(onClick = onOpenSettings) {
            LucideIcon(Lucide.Settings, stringResource(R.string.nav_settings), tint = white)
        }
    }
}

/** The ProShot-style value strip under the viewfinder. */
@Composable
private fun ReadoutBar(
    params: CameraParams,
    meter: LiveMeter,
    info: CameraInfo?,
    active: ControlPanel,
    onSelect: (ControlPanel) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Readout(
            label = "ISO",
            value = (if (params.isoAuto) meter.iso else params.iso)?.toString() ?: "—",
            auto = params.isoAuto,
            selected = active == ControlPanel.ISO,
            enabled = info?.supportsManualExposure != false,
            onClick = { onSelect(ControlPanel.ISO) },
        )
        Readout(
            label = "S",
            value = Formatters.shutter(if (params.shutterAuto) meter.exposureNs else params.exposureNs),
            auto = params.shutterAuto,
            selected = active == ControlPanel.SHUTTER,
            enabled = info?.supportsManualExposure != false,
            onClick = { onSelect(ControlPanel.SHUTTER) },
        )
        Readout(
            label = "EV",
            value = Formatters.ev(params.exposureCompensation),
            auto = params.exposureCompensation == 0f && !params.aeLock,
            selected = active == ControlPanel.EV,
            onClick = { onSelect(ControlPanel.EV) },
        )
        Readout(
            label = "WB",
            value = when (params.whiteBalance) {
                WhiteBalance.AUTO -> stringResource(R.string.wb_auto)
                WhiteBalance.MANUAL -> Formatters.kelvin(params.kelvin)
                else -> Formatters.kelvin(params.whiteBalance.kelvin)
            },
            auto = params.whiteBalance == WhiteBalance.AUTO,
            selected = active == ControlPanel.WB,
            onClick = { onSelect(ControlPanel.WB) },
        )
        Readout(
            label = if (params.focusMode == FocusMode.MANUAL) "MF" else "AF",
            value = Formatters.focusDistance(if (params.focusMode == FocusMode.MANUAL) params.focusDistance else meter.focusDistance),
            auto = params.focusMode != FocusMode.MANUAL,
            selected = active == ControlPanel.FOCUS,
            enabled = info?.hasAutoFocus != false,
            onClick = { onSelect(ControlPanel.FOCUS) },
        )
        Readout(
            label = "ZOOM",
            value = Formatters.zoom(params.zoom),
            auto = params.zoom == 1f,
            selected = active == ControlPanel.ZOOM,
            onClick = { onSelect(ControlPanel.ZOOM) },
        )
    }
}

@Composable
private fun Readout(
    label: String,
    value: String,
    auto: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (auto) Color.White.copy(alpha = 0.6f) else accent, style = MaterialTheme.typography.labelSmall)
            if (auto) Text(" A", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun ThumbnailImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = (context.applicationContext as com.rawvision.camera.RawVisionApp).container
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) { bitmap = container.thumbnails.load(path, 256) }
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Color.White.copy(alpha = 0.08f)))
    }
}
