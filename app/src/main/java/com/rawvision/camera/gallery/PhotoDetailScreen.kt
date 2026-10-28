@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.gallery

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawvision.camera.R
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.editor.Exporter
import com.rawvision.camera.editor.rememberSaveToDevice
import com.rawvision.camera.ui.components.ConfirmDialog
import com.rawvision.camera.ui.components.InfoRow
import com.rawvision.camera.ui.components.RvTopBar
import com.rawvision.camera.ui.components.SectionCard
import com.rawvision.camera.ui.components.SectionHeader
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.Formatters
import java.io.File

@Composable
fun PhotoDetailScreen(
    vm: GalleryViewModel,
    photoId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsStateWithLifecycle()
    val exports by vm.exports.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val photo = photos.firstOrNull { it.id == photoId }
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var editAuthor by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }

    LaunchedEffect(photoId) { vm.loadExports(photoId) }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    val shareTitle = stringResource(R.string.action_share)
    val savedToDevice = stringResource(R.string.saved_to_device)
    val saveJpegToDevice = rememberSaveToDevice { photo?.let { vm.exportToDevice(it, savedToDevice) } }
    val saveDngToDevice = rememberSaveToDevice {
        photo?.rawPath?.let { vm.saveToDevice(File(it), "image/x-adobe-dng", savedToDevice) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RvTopBar(
                title = photo?.displayName ?: "",
                onBack = onBack,
                backDescription = stringResource(R.string.action_back),
                actions = {
                    if (photo != null) {
                        IconButton(onClick = { vm.toggleFavorite(photo) }) {
                            LucideIcon(Lucide.Heart, stringResource(R.string.action_favorite),
                                tint = if (photo.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { onEdit(photo.id) }) { LucideIcon(Lucide.Sliders, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { LucideIcon(Lucide.Trash, stringResource(R.string.action_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        if (photo == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.photo_missing)) }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Preview ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Color.Black)
                    .clickable { onEdit(photo.id) },
                contentAlignment = Alignment.Center,
            ) {
                var bitmap by remember(photo.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(photo.id) {
                    bitmap = (photo.thumbPath ?: photo.jpegPath)?.let { vm.thumbnails.load(it, 1024) }
                }
                val bmp = bitmap
                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(color = Color.White)
            }

            // ---- Actions ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onEdit(photo.id) }, modifier = Modifier.weight(1f)) {
                    LucideIcon(Lucide.Sliders, null, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_edit))
                }
                Button(
                    onClick = {
                        vm.exportForShare(photo) { file ->
                            context.startActivity(Intent.createChooser(Exporter.shareIntent(context, file), shareTitle))
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else LucideIcon(Lucide.Share, null, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = saveJpegToDevice, enabled = !busy) {
                    LucideIcon(Lucide.Download, null, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_save_device))
                }
            }
            if (photo.rawPath != null) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(
                        onClick = {
                            context.startActivity(Intent.createChooser(Exporter.shareIntent(context, File(photo.rawPath), "image/x-adobe-dng"), shareTitle))
                        },
                    ) {
                        LucideIcon(Lucide.Film, null, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.photo_share_dng))
                    }
                    TextButton(onClick = saveDngToDevice) {
                        LucideIcon(Lucide.Download, null, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.photo_save_dng))
                    }
                }
            }

            // ---- Rating ----
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.photo_rating), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                (1..5).forEach { star ->
                    IconButton(onClick = { vm.setRating(photo, if (photo.rating == star) 0 else star) }) {
                        LucideIcon(Lucide.Star, null, size = 20.dp,
                            tint = if (star <= photo.rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // ---- Capture info ----
            SectionHeader(stringResource(R.string.photo_section_capture))
            SectionCard {
                InfoRow(stringResource(R.string.photo_date), Formatters.dateTime(photo.createdAt), Lucide.Calendar)
                InfoRow(stringResource(R.string.photo_size), "${photo.width} × ${photo.height}  ·  ${Formatters.megapixels(photo.width, photo.height)}", Lucide.Maximize)
                InfoRow("ISO", photo.iso?.toString() ?: "—", Lucide.Gauge)
                InfoRow(stringResource(R.string.camera_shutter), Formatters.shutter(photo.exposureNs), Lucide.Timer)
                InfoRow(stringResource(R.string.photo_aperture), Formatters.aperture(photo.aperture), Lucide.Aperture)
                InfoRow(stringResource(R.string.photo_focal_length), Formatters.focalLength(photo.focalLengthMm), Lucide.Focus)
                InfoRow(stringResource(R.string.camera_focus), Formatters.focusDistance(photo.focusDistance), Lucide.Crosshair)
                InfoRow("WB", photo.whiteBalanceMode + (photo.whiteBalanceKelvin?.let { "  ·  ${it}K" } ?: ""), Lucide.Thermometer)
                InfoRow(stringResource(R.string.camera_exposure_comp), Formatters.ev(photo.exposureCompensation), Lucide.SunMedium)
                InfoRow(stringResource(R.string.camera_flash), photo.flashMode, Lucide.Zap)
                InfoRow("Zoom", Formatters.zoom(photo.zoomRatio), Lucide.ZoomIn)
                InfoRow(stringResource(R.string.photo_mode), photo.captureMode, Lucide.Sliders)
            }

            // ---- Location & author ----
            SectionHeader(stringResource(R.string.photo_section_meta))
            SectionCard {
                InfoRow(stringResource(R.string.photo_location), Formatters.coordinates(photo.latitude, photo.longitude), Lucide.MapPin)
                photo.altitude?.let { InfoRow(stringResource(R.string.photo_altitude), String.format(java.util.Locale.US, "%.0f m", it), Lucide.Mountain) }
                InfoRow(stringResource(R.string.settings_author), photo.author ?: "—", Lucide.User)
                InfoRow(stringResource(R.string.settings_copyright), photo.copyright ?: "—", Lucide.Copyright)
                InfoRow(stringResource(R.string.photo_device), listOfNotNull(photo.deviceMake, photo.deviceModel).joinToString(" "), Lucide.Smartphone)
                InfoRow(stringResource(R.string.photo_camera_id), "${photo.cameraId} · ${photo.lensFacing.name}", Lucide.Camera)
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { editAuthor = true }) { Text(stringResource(R.string.photo_edit_author)) }
                    TextButton(onClick = { editNotes = true }) { Text(stringResource(R.string.photo_edit_notes)) }
                }
                if (photo.notes.isNotBlank()) {
                    Text(photo.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            // ---- Files ----
            SectionHeader(stringResource(R.string.photo_section_files))
            SectionCard {
                photo.rawPath?.let { InfoRow("DNG", fileSize(it), Lucide.Film) }
                photo.jpegPath?.let { InfoRow("JPEG", fileSize(it), Lucide.FileImage) }
                photo.sidecarPath?.let { InfoRow("XMP", fileSize(it), Lucide.FileText) }
                exports.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LucideIcon(Lucide.Download, null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(File(e.path).name, style = MaterialTheme.typography.bodyMedium)
                            Text("${e.width} × ${e.height} · ${Formatters.bytes(e.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { context.startActivity(Intent.createChooser(Exporter.shareIntent(context, File(e.path)), shareTitle)) }) {
                            LucideIcon(Lucide.Share, stringResource(R.string.action_share), size = 18.dp)
                        }
                        IconButton(onClick = { vm.saveToDevice(File(e.path), "image/jpeg", savedToDevice) }) {
                            LucideIcon(Lucide.Download, stringResource(R.string.action_save_device), size = 18.dp)
                        }
                        IconButton(onClick = { vm.deleteExport(e) }) { LucideIcon(Lucide.Trash, stringResource(R.string.action_delete), size = 18.dp) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && photo != null) {
        ConfirmDialog(
            title = stringResource(R.string.photo_delete_title),
            message = stringResource(R.string.photo_delete_body),
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { confirmDelete = false; vm.delete(photo) { onBack() } },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
    if (editAuthor && photo != null) {
        TextPairDialog(
            title = stringResource(R.string.photo_edit_author),
            label1 = stringResource(R.string.settings_author), initial1 = photo.author ?: "",
            label2 = stringResource(R.string.settings_copyright), initial2 = photo.copyright ?: "",
            onDismiss = { editAuthor = false },
            onConfirm = { a, c -> editAuthor = false; vm.setAuthor(photo, a, c) },
        )
    }
    if (editNotes && photo != null) {
        TextPairDialog(
            title = stringResource(R.string.photo_edit_notes),
            label1 = stringResource(R.string.photo_notes), initial1 = photo.notes,
            label2 = null, initial2 = "",
            onDismiss = { editNotes = false },
            onConfirm = { n, _ -> editNotes = false; vm.setNotes(photo, n) },
        )
    }
}

private fun fileSize(path: String): String {
    val f = File(path)
    return if (f.exists()) Formatters.bytes(f.length()) else "—"
}

@Composable
fun TextPairDialog(
    title: String,
    label1: String,
    initial1: String,
    label2: String?,
    initial2: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var v1 by remember { mutableStateOf(initial1) }
    var v2 by remember { mutableStateOf(initial2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = v1, onValueChange = { v1 = it }, label = { Text(label1) }, singleLine = label2 != null, modifier = Modifier.fillMaxWidth())
                if (label2 != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = v2, onValueChange = { v2 = it }, label = { Text(label2) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(v1, v2) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
