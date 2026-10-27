package com.rawvision.camera.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Tiny in-memory LRU cache for gallery thumbnails (no third-party image loader needed). */
class ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(path: String, targetPx: Int = 512): Bitmap? {
        val key = "$path@$targetPx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) return@withContext null
            val bmp = ImageUtils.decodeSampled(file, targetPx, targetPx) ?: return@withContext null
            cache.put(key, bmp)
            bmp
        }
    }

    fun evict(path: String) {
        cache.snapshot().keys.filter { it.startsWith(path) }.forEach { cache.remove(it) }
    }

    fun clear() = cache.evictAll()
}
