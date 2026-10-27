package com.rawvision.camera.data

import android.content.Context
import com.rawvision.camera.data.db.AppDatabase
import com.rawvision.camera.data.repo.PhotoRepository
import com.rawvision.camera.data.repo.SettingsRepository
import com.rawvision.camera.data.storage.AppStorage
import com.rawvision.camera.util.ThumbnailCache
import kotlinx.coroutines.flow.MutableSharedFlow

/** Simple service locator that owns every long-lived object of the app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val storage = AppStorage(appContext)
    val database = AppDatabase(appContext, storage)
    val settings = SettingsRepository(database)
    val photos = PhotoRepository(database, storage)
    val thumbnails = ThumbnailCache()

    /** Hardware volume-key presses forwarded by the activity so the camera can use them as a shutter. */
    val volumeKeyPresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
