package com.rawvision.camera.camera

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * SurfaceView-backed viewfinder. The buffer is fixed to the camera's preview
 * size (landscape); the framework rotates it for the portrait display, so the
 * view itself uses the swapped aspect ratio.
 */
@Composable
fun CameraPreview(
    controller: CameraController,
    info: CameraInfo,
    modifier: Modifier = Modifier,
) {
    val size = info.previewSize
    val aspect = size.height.toFloat() / size.width.toFloat()

    // Keyed on the camera id so a lens switch creates a fresh SurfaceView with the right buffer size.
    key(info.id) {
        AndroidView(
            modifier = modifier.aspectRatio(aspect),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.setFixedSize(size.width, size.height)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            controller.setPreviewSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            controller.removePreviewSurface(holder.surface)
                        }
                    })
                }
            },
        )
    }
}
