@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rawvision.camera.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rawvision.camera.R
import com.rawvision.camera.camera.CameraInfo
import com.rawvision.camera.ui.components.RvTopBar
import com.rawvision.camera.ui.theme.Lucide
import com.rawvision.camera.ui.theme.LucideIcon
import kotlinx.coroutines.launch

/** Plain-text dump of every camera the device exposes, with copy and share. */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val text = remember { CameraInfo.diagnostics(CameraInfo.enumerate(context)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.diagnostics_copied)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RvTopBar(
                title = stringResource(R.string.settings_diagnostics),
                onBack = onBack,
                backDescription = stringResource(R.string.action_back),
                actions = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("RAWVision diagnostics", text))
                        scope.launch { snackbar.showSnackbar(copied) }
                    }) { LucideIcon(Lucide.Copy, null) }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "RAWVision camera diagnostics")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }) { LucideIcon(Lucide.Share, stringResource(R.string.action_share)) }
                },
            )
        },
    ) { padding ->
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}
