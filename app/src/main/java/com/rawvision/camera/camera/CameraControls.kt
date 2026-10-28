@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rawvision.camera.R
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.Formatters
import kotlin.math.ln
import kotlin.math.roundToInt

/** Expanded control for one shooting parameter (ISO, shutter, …). */
@Composable
fun ControlPanelContent(
    panel: ControlPanel,
    params: CameraParams,
    meter: LiveMeter,
    info: CameraInfo,
    vm: CameraViewModel,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        when (panel) {
            ControlPanel.ISO -> IsoControl(params, meter, info, vm)
            ControlPanel.SHUTTER -> ShutterControl(params, meter, info, vm)
            ControlPanel.EV -> EvControl(params, info, vm)
            ControlPanel.WB -> WhiteBalanceControl(params, info, vm)
            ControlPanel.FOCUS -> FocusControl(params, meter, info, vm)
            ControlPanel.ZOOM -> ZoomControl(params, info, vm)
            ControlPanel.NONE -> Unit
        }
    }
}

@Composable
private fun PanelHeader(title: String, value: String, auto: Boolean?, onAutoToggle: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(10.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (auto != null && onAutoToggle != null) {
            Chip(text = stringResource(R.string.camera_auto), selected = auto, onClick = onAutoToggle)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, icon: Int? = null) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LucideIcon(icon, null, size = 14.dp, tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DarkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            disabledThumbColor = Color.White.copy(alpha = 0.4f),
            disabledActiveTrackColor = Color.White.copy(alpha = 0.2f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.1f),
        ),
    )
}

@Composable
private fun IsoControl(params: CameraParams, meter: LiveMeter, info: CameraInfo, vm: CameraViewModel) {
    val range = info.isoRange
    val stops = remember(range) {
        if (range == null) emptyList() else CameraParams.ISO_STOPS.filter { it in range.lower..range.upper }.ifEmpty { listOf(range.lower, range.upper) }
    }
    val shown = if (params.isoAuto) meter.iso ?: params.iso else params.iso
    PanelHeader("ISO", shown.toString(), params.isoAuto) {
        vm.setParams { p -> p.copy(isoAuto = !p.isoAuto, iso = if (p.isoAuto) ExposureMath.nearestIsoStop(meter.iso ?: p.iso) else p.iso) }
    }
    if (stops.isNotEmpty()) {
        val index = stops.indexOfNearest(shown.toDouble()) { it.toDouble() }
        DarkSlider(
            value = index.toFloat(),
            onValueChange = { v -> vm.setParams { it.copy(isoAuto = false, iso = stops[v.roundToInt().coerceIn(0, stops.lastIndex)]) } },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            enabled = info.supportsManualExposure,
        )
    }
}

@Composable
private fun ShutterControl(params: CameraParams, meter: LiveMeter, info: CameraInfo, vm: CameraViewModel) {
    val range = info.exposureRange
    val stops = remember(range) {
        if (range == null) emptyList() else CameraParams.SHUTTER_STOPS.filter { it in range.lower..range.upper }.ifEmpty { listOf(range.lower, range.upper) }
    }
    val shown = if (params.shutterAuto) meter.exposureNs ?: params.exposureNs else params.exposureNs
    PanelHeader(stringResource(R.string.camera_shutter), Formatters.shutter(shown), params.shutterAuto) {
        vm.setParams { p -> p.copy(shutterAuto = !p.shutterAuto, exposureNs = if (p.shutterAuto) ExposureMath.nearestShutterStop(meter.exposureNs ?: p.exposureNs) else p.exposureNs) }
    }
    if (stops.isNotEmpty()) {
        val index = stops.indexOfNearest(shown.toDouble()) { it.toDouble() }
        DarkSlider(
            value = index.toFloat(),
            onValueChange = { v -> vm.setParams { it.copy(shutterAuto = false, exposureNs = stops[v.roundToInt().coerceIn(0, stops.lastIndex)]) } },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            enabled = info.supportsManualExposure,
        )
    }
}

@Composable
private fun EvControl(params: CameraParams, info: CameraInfo, vm: CameraViewModel) {
    val step = info.aeCompensationStep.takeIf { it > 0f } ?: 0.333f
    val min = info.aeCompensationRange.lower * step
    val max = info.aeCompensationRange.upper * step
    val stepsCount = (info.aeCompensationRange.upper - info.aeCompensationRange.lower - 1).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PanelHeaderInline(stringResource(R.string.camera_exposure_comp), Formatters.ev(params.exposureCompensation))
        Spacer(Modifier.weight(1f))
        Chip(text = "AE-L", selected = params.aeLock, icon = if (params.aeLock) Lucide.Lock else Lucide.LockOpen,
            onClick = { vm.setParams { it.copy(aeLock = !it.aeLock) } })
        Spacer(Modifier.width(8.dp))
        Chip(text = "0", selected = false, onClick = { vm.setParams { it.copy(exposureCompensation = 0f) } })
    }
    if (max > min) {
        DarkSlider(
            value = params.exposureCompensation.coerceIn(min, max),
            onValueChange = { v -> vm.setParams { it.copy(exposureCompensation = (v / step).roundToInt() * step) } },
            valueRange = min..max,
            steps = stepsCount,
        )
    }
}

@Composable
private fun PanelHeaderInline(title: String, value: String) {
    Text(title, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.width(10.dp))
    Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
}

@Composable
private fun WhiteBalanceControl(params: CameraParams, info: CameraInfo, vm: CameraViewModel) {
    PanelHeader(
        "WB",
        if (params.whiteBalance == WhiteBalance.MANUAL) Formatters.kelvin(params.kelvin) else stringResource(wbLabel(params.whiteBalance)),
        null, null,
    )
    Spacer(Modifier.padding(4.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhiteBalance.entries.forEach { wb ->
            val available = wb == WhiteBalance.AUTO ||
                (wb == WhiteBalance.MANUAL && info.supportsManualPostProcessing) ||
                info.awbModes.contains(wb.awbMode)
            if (available) {
                Chip(
                    text = stringResource(wbLabel(wb)),
                    selected = params.whiteBalance == wb,
                    icon = wbIcon(wb),
                    onClick = { vm.setParams { it.copy(whiteBalance = wb) } },
                )
            }
        }
    }
    if (params.whiteBalance == WhiteBalance.MANUAL) {
        Spacer(Modifier.padding(4.dp))
        Text(stringResource(R.string.edit_temperature) + " · " + Formatters.kelvin(params.kelvin), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        DarkSlider(
            value = params.kelvin.toFloat(),
            onValueChange = { v -> vm.setParams { it.copy(kelvin = (v / 50f).roundToInt() * 50) } },
            valueRange = 2000f..10000f,
        )
        Text(stringResource(R.string.edit_tint) + " · " + params.tint.roundToInt(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        DarkSlider(
            value = params.tint,
            onValueChange = { v -> vm.setParams { it.copy(tint = v.roundToInt().toFloat()) } },
            valueRange = -100f..100f,
        )
    }
}

fun wbLabel(wb: WhiteBalance): Int = when (wb) {
    WhiteBalance.AUTO -> R.string.wb_auto
    WhiteBalance.INCANDESCENT -> R.string.wb_incandescent
    WhiteBalance.FLUORESCENT -> R.string.wb_fluorescent
    WhiteBalance.DAYLIGHT -> R.string.wb_daylight
    WhiteBalance.CLOUDY -> R.string.wb_cloudy
    WhiteBalance.SHADE -> R.string.wb_shade
    WhiteBalance.MANUAL -> R.string.wb_manual
}

fun wbIcon(wb: WhiteBalance): Int = when (wb) {
    WhiteBalance.AUTO -> Lucide.Wand
    WhiteBalance.INCANDESCENT -> Lucide.Lightbulb
    WhiteBalance.FLUORESCENT -> Lucide.Lamp
    WhiteBalance.DAYLIGHT -> Lucide.Sun
    WhiteBalance.CLOUDY -> Lucide.Cloud
    WhiteBalance.SHADE -> Lucide.CloudSun
    WhiteBalance.MANUAL -> Lucide.Thermometer
}

@Composable
private fun FocusControl(params: CameraParams, meter: LiveMeter, info: CameraInfo, vm: CameraViewModel) {
    val manual = params.focusMode == FocusMode.MANUAL
    val shown = if (manual) params.focusDistance else meter.focusDistance
    PanelHeader(stringResource(R.string.camera_focus), Formatters.focusDistance(shown), !manual) {
        vm.setParams { p ->
            if (p.focusMode == FocusMode.MANUAL) p.copy(focusMode = FocusMode.CONTINUOUS)
            else p.copy(focusMode = FocusMode.MANUAL, focusDistance = (meter.focusDistance ?: 0f).coerceIn(0f, info.manualFocusMax))
        }
    }
    if (info.hasAutoFocus) {
        // Slider runs from macro (left) to infinity (right).
        val maxD = info.manualFocusMax
        DarkSlider(
            value = maxD - (shown ?: 0f).coerceIn(0f, maxD),
            onValueChange = { v -> vm.setParams { it.copy(focusMode = FocusMode.MANUAL, focusDistance = (maxD - v).coerceIn(0f, maxD)) } },
            valueRange = 0f..maxD,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.Flower, null, size = 14.dp, tint = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.camera_macro), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("∞", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(4.dp))
                LucideIcon(Lucide.Mountain, null, size = 14.dp, tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ZoomControl(params: CameraParams, info: CameraInfo, vm: CameraViewModel) {
    PanelHeader("Zoom", Formatters.zoom(params.zoom), null, null)
    DarkSlider(
        value = params.zoom.coerceIn(info.minZoom, info.maxZoom),
        onValueChange = { vm.setZoom(it) },
        valueRange = info.minZoom..info.maxZoom.coerceAtLeast(info.minZoom + 0.01f),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1f, 2f, 3f, 5f).filter { it <= info.maxZoom }.forEach { z ->
            Chip(text = Formatters.zoom(z), selected = params.zoom == z, onClick = { vm.setZoom(z) })
        }
    }
}

/** Index of the element whose log-distance to [target] is smallest (good for stop tables). */
private fun <T> List<T>.indexOfNearest(target: Double, value: (T) -> Double): Int {
    if (isEmpty()) return 0
    var best = 0
    var bestErr = Double.MAX_VALUE
    forEachIndexed { i, t ->
        val v = value(t)
        val err = kotlin.math.abs(ln(v.coerceAtLeast(1e-9) / target.coerceAtLeast(1e-9)))
        if (err < bestErr) { bestErr = err; best = i }
    }
    return best
}
