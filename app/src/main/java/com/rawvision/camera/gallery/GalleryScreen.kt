@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rawvision.camera.R
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.ui.components.EmptyState
import com.rawvision.camera.ui.components.RvTopBar
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import com.rawvision.camera.util.ThumbnailCache

@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onOpenCamera: () -> Unit,
) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val shown = remember(photos, filter) { vm.filtered(photos, filter) }

    Scaffold(
        topBar = {
            RvTopBar(
                title = stringResource(R.string.nav_gallery),
                onBack = onBack,
                backDescription = stringResource(R.string.action_back),
                actions = {
                    IconButton(onClick = onOpenCamera) { LucideIcon(Lucide.Camera, stringResource(R.string.nav_camera)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GalleryFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text(stringResource(filterLabel(f))) },
                        leadingIcon = { LucideIcon(filterIcon(f), null, size = 16.dp) },
                    )
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    icon = Lucide.Images,
                    title = stringResource(R.string.gallery_empty_title),
                    message = stringResource(R.string.gallery_empty_body),
                    action = { Button(onClick = onOpenCamera) { Text(stringResource(R.string.gallery_take_photo)) } },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { photo ->
                        PhotoTile(photo, vm.thumbnails) { onOpenPhoto(photo.id) }
                    }
                }
            }
        }
    }
}

private fun filterLabel(f: GalleryFilter): Int = when (f) {
    GalleryFilter.ALL -> R.string.gallery_filter_all
    GalleryFilter.FAVORITES -> R.string.gallery_filter_favorites
    GalleryFilter.EDITED -> R.string.gallery_filter_edited
    GalleryFilter.RAW -> R.string.gallery_filter_raw
}

private fun filterIcon(f: GalleryFilter): Int = when (f) {
    GalleryFilter.ALL -> Lucide.LayoutGrid
    GalleryFilter.FAVORITES -> Lucide.Heart
    GalleryFilter.EDITED -> Lucide.Sliders
    GalleryFilter.RAW -> Lucide.Film
}

@Composable
fun PhotoTile(photo: Photo, cache: ThumbnailCache, onClick: () -> Unit) {
    var bitmap by remember(photo.thumbPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photo.thumbPath) {
        bitmap = photo.thumbPath?.let { cache.load(it, 320) }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = photo.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LucideIcon(Lucide.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (photo.isRaw) Badge("RAW")
            if (photo.hasEdits) Badge(null, Lucide.Sliders)
            if (photo.favorite) Badge(null, Lucide.Heart)
        }
    }
}

@Composable
private fun Badge(text: String?, icon: Int? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        if (text != null) Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
        if (icon != null) LucideIcon(icon, null, size = 12.dp, tint = Color.White)
    }
}
