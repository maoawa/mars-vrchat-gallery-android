package cc.maao.vrchat.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Composable
fun CachedNetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetPixelSize: Int = 1_200,
    backgroundColor: Color? = null,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, url, targetPixelSize) {
        value = GalleryImageCache.load(context, url, targetPixelSize)
    }

    Box(
        modifier = modifier.background(backgroundColor ?: Color(0xFF1B1B1E)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            LoadingPlaceholder()
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

@Composable
private fun BoxScope.LoadingPlaceholder() {
    Text(
        text = "...",
        color = Color(0xFFC9C2BA),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.Center),
    )
}

object GalleryImageCache {
    private const val DefaultTargetPixelSize = 1_200
    private val downloadSemaphore = Semaphore(4)
    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(
        context: Context,
        url: String,
        targetPixelSize: Int = DefaultTargetPixelSize,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val memoryKey = "$url@$targetPixelSize"
        memoryCache.get(memoryKey)?.let { return@withContext it }

        val bytes = readOrDownload(context, url) ?: return@withContext null
        val bitmap = bytes.decodeSampledBitmap(targetPixelSize)

        if (bitmap != null) {
            memoryCache.put(memoryKey, bitmap)
        }

        bitmap
    }

    suspend fun prefetch(
        context: Context,
        urls: List<String>,
        targetPixelSize: Int = DefaultTargetPixelSize,
    ) = withContext(Dispatchers.IO) {
        urls.distinct().forEach { url ->
            val memoryKey = "$url@$targetPixelSize"
            if (memoryCache.get(memoryKey) != null) {
                return@forEach
            }

            readOrDownload(context, url)
                ?.decodeSampledBitmap(targetPixelSize)
                ?.let { bitmap -> memoryCache.put(memoryKey, bitmap) }
        }
    }

    suspend fun ensureDiskCached(context: Context, url: String) = withContext(Dispatchers.IO) {
        readOrDownload(context, url)
    }

    fun clear(context: Context) {
        memoryCache.evictAll()
        imageCacheDir(context).deleteRecursively()
    }

    private suspend fun readOrDownload(context: Context, url: String): ByteArray? {
        val cacheFile = File(imageCacheDir(context), url.sha256())
        if (cacheFile.exists()) {
            return cacheFile.readBytes()
        }

        return downloadSemaphore.withPermit {
            if (cacheFile.exists()) {
                cacheFile.readBytes()
            } else {
                download(url)?.also { bytes ->
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeBytes(bytes)
                }
            }
        }
    }

    private fun ByteArray.decodeSampledBitmap(targetPixelSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return BitmapFactory.decodeByteArray(this, 0, size)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(maxOf(bounds.outWidth, bounds.outHeight), targetPixelSize)
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return BitmapFactory.decodeByteArray(this, 0, size, options)
    }

    private fun calculateInSampleSize(maxDimension: Int, targetPixelSize: Int): Int {
        val safeTarget = targetPixelSize.coerceAtLeast(320)
        var sampleSize = 1

        while (maxDimension / sampleSize > safeTarget * 2) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun download(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.use { it.readBytes() }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun imageCacheDir(context: Context): File {
        return File(context.applicationContext.cacheDir, "gallery_images")
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun memoryCacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemoryKb / 6
    }
}
