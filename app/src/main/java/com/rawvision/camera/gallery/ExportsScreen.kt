@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.gallery

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawvision.camera.R
import com.rawvision.camera.data.model.ExportRecord
import com.rawvision.camera.editor.Exporter
import com.rawvision.camera.ui.components.ConfirmDialog
import com.rawvision.camera.ui.components.EmptyState
import com.rawvision.camera.ui.components.RvTopBar
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.Formatters
import java.io.File

/** Every rendered export kept inside the app folder, with share / save / delete. */
@Composable
fun ExportsScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
) {
    val context = LocalContext.current
    val exports by vm.allExports.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<ExportRecord?>(null) }
    val shareTitle = stringResource(R.string.action_share)
    val savedToDevice = stringResource(R.string.saved_to_device)

    LaunchedEffect(Unit) { vm.loadAllExports() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { RvTopBar(title = stringResource(R.string.nav_exports), onBack = onBack, backDescription = stringResource(R.string.action_back)) },
    ) { padding ->
        if (exports.isEmpty()) {
            EmptyState(
                icon = Lucide.FolderOpen,
                title = stringResource(R.string.exports_empty_title),
                message = stringResource(R.string.exports_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(exports, key = { it.first.id }) { (record, baseName) ->
                ExportRow(
                    record = record,
                    baseName = baseName,
                    thumbnails = vm.thumbnails,
                    onOpen = { onOpenPhoto(record.photoId) },
                    onShare = { context.startActivity(Intent.createChooser(Exporter.shareIntent(context, File(record.path)), shareTitle)) },
                    onSave = { vm.saveToDevice(File(record.path), "image/jpeg", savedToDevice) },
                    onDelete = { pendingDelete = record },
                )
            }
        }
    }

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = stringResource(R.string.exports_delete_title),
            message = stringResource(R.string.exports_delete_body),
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { pendingDelete = null; vm.deleteExport(record) },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun ExportRow(
    record: ExportRecord,
    baseName: String,
    thumbnails: com.rawvision.camera.util.ThumbnailCache,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var bitmap by remember(record.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(record.path) { bitmap = thumbnails.load(record.path, 256) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(File(record.path).name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "$baseName · ${record.width} × ${record.height} · ${Formatters.bytes(record.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(Formatters.dateTime(record.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onShare) { LucideIcon(Lucide.Share, stringResource(R.string.action_share), size = 20.dp) }
        IconButton(onClick = onSave) { LucideIcon(Lucide.Download, stringResource(R.string.action_save_device), size = 20.dp) }
        IconButton(onClick = onDelete) { LucideIcon(Lucide.Trash, stringResource(R.string.action_delete), size = 20.dp) }
    }
}
