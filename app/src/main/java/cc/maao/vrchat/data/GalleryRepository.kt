package cc.maao.vrchat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class GalleryRepository(
    context: Context,
    private val baseUrl: String = "https://vrchat.marsinside.com",
) {
    private val appContext = context.applicationContext
    private val jsonCacheDir = File(File(appContext.cacheDir, "gallery_json"), baseUrl.sha256())
    private val dataPrefixes = listOf("data", "src/data")

    suspend fun loadGallery(): GalleryContent = withContext(Dispatchers.IO) {
        val friends = parseFriends(readJson("friends.json"))
        val worlds = parseWorlds(readJson("worlds.json"))
        val photos = parseImages(readJson("images.json")).sortedByDescending { it.captured }
        val specialEventRecords = parseSpecialEvents(readJson("special-events.json"))
        val photosById = photos.associateBy { it.id }
        val rows = photos
            .filter { it.parent == null && !it.specialEvent }
            .map { photo ->
                GalleryRow(
                    photo = photo,
                    linkedPhotos = photo.linkedIds.mapNotNull { photosById[it] },
                )
            }
        val specialEvents = specialEventRecords
            .mapNotNull { event ->
                val eventPhotos = event.photoIds.mapNotNull { photosById[it] }
                val featuredPhotos = event.featuredPhotoIds.mapNotNull { photosById[it] }
                if (eventPhotos.isEmpty() || featuredPhotos.isEmpty()) {
                    null
                } else {
                    val featuredIds = featuredPhotos.map { it.id }.toSet()
                    SpecialEventView(
                        event = event,
                        photos = eventPhotos,
                        featuredPhotos = featuredPhotos,
                        linkedPhotos = eventPhotos.filterNot { it.id in featuredIds },
                        sortKey = eventPhotos.maxOf { it.captured },
                    )
                }
            }
            .sortedByDescending { it.sortKey }

        GalleryContent(
            friends = friends,
            worlds = worlds,
            photos = photos,
            rows = rows,
            specialEvents = specialEvents,
            flowItems = buildFlowItems(rows, specialEvents),
        )
    }

    fun clearCache() {
        jsonCacheDir.deleteRecursively()
    }

    private fun buildFlowItems(
        rows: List<GalleryRow>,
        events: List<SpecialEventView>,
    ): List<GalleryFlowItem> {
        if (events.isEmpty()) {
            return listOf(GalleryFlowItem.Rows("all", rows))
        }

        val flowItems = mutableListOf<GalleryFlowItem>()
        var rowCursor = 0

        events.forEach { event ->
            val rowsBeforeEvent = mutableListOf<GalleryRow>()
            while (rowCursor < rows.size && rows[rowCursor].photo.captured > event.sortKey) {
                rowsBeforeEvent += rows[rowCursor]
                rowCursor += 1
            }

            if (rowsBeforeEvent.isNotEmpty()) {
                flowItems += GalleryFlowItem.Rows("gallery-before-${event.event.id}", rowsBeforeEvent)
            }
            flowItems += GalleryFlowItem.Event(event)
        }

        val remainingRows = rows.drop(rowCursor)
        if (remainingRows.isNotEmpty()) {
            flowItems += GalleryFlowItem.Rows("gallery-after-special-events", remainingRows)
        }

        return flowItems
    }

    private fun readJson(fileName: String): String {
        jsonCacheDir.mkdirs()
        val cacheFile = File(jsonCacheDir, fileName)
        var lastFailure: Exception? = null

        dataPrefixes.forEach { prefix ->
            try {
                val text = requestText("$baseUrl/$prefix/$fileName")
                cacheFile.writeText(text)
                return text
            } catch (exception: Exception) {
                lastFailure = exception
            }
        }

        if (cacheFile.exists()) {
            return cacheFile.readText()
        }

        throw IOException("Unable to load $fileName from $baseUrl", lastFailure)
    }

    private fun requestText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }

        return connection.use {
            val code = responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code for $url")
            }
            inputStream.bufferedReader().use { reader -> reader.readText() }
        }
    }

    private inline fun <T> HttpURLConnection.use(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            disconnect()
        }
    }

    private fun parseFriends(text: String): List<Friend> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Friend(
                id = item.getString("id"),
                nameEn = item.getString("name_en"),
                nameZh = item.optNullableString("name_zh"),
            )
        }
    }

    private fun parseWorlds(text: String): List<World> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            World(
                id = item.getString("id"),
                nameEn = item.getString("name_en"),
                nameZh = item.optNullableString("name_zh"),
            )
        }
    }

    private fun parseImages(text: String): List<GalleryImage> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            GalleryImage(
                id = item.getInt("id"),
                filename = item.getString("filename"),
                captured = item.getString("captured"),
                world = item.optString("world"),
                specialEvent = item.optBoolean("special-events", false),
                descriptionEn = item.optNullableString("description_en"),
                descriptionZh = item.optNullableString("description_zh"),
                friendIds = item.optStringArray("friend"),
                linkedIds = item.optIntArray("linked"),
                parent = item.optNullableInt("parent"),
            )
        }
    }

    private fun parseSpecialEvents(text: String): List<SpecialEvent> {
        val array = JSONArray(text)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SpecialEvent(
                id = item.getString("id"),
                titleEn = item.getString("title_en"),
                titleZh = item.optNullableString("title_zh"),
                dateEn = item.getString("date_en"),
                dateZh = item.optNullableString("date_zh"),
                showFullDate = item.optBoolean("show_full_date", false),
                world = item.optNullableString("world"),
                friendIds = item.optStringArray("friends"),
                descriptionEn = item.optNullableString("description_en"),
                descriptionZh = item.optNullableString("description_zh"),
                photoIds = item.optIntArray("photo_ids"),
                featuredPhotoIds = item.optIntArray("featured_photo_ids"),
            )
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }

    private fun JSONObject.optStringArray(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun JSONObject.optIntArray(name: String): List<Int> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getInt(it) }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
