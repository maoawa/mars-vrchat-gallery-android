package cc.maao.vrchat.data

data class Friend(
    val id: String,
    val nameEn: String,
    val nameZh: String?,
)

data class World(
    val id: String,
    val nameEn: String,
    val nameZh: String?,
)

data class GalleryImage(
    val id: Int,
    val filename: String,
    val captured: String,
    val world: String,
    val specialEvent: Boolean,
    val descriptionEn: String?,
    val descriptionZh: String?,
    val friendIds: List<String>,
    val linkedIds: List<Int>,
    val parent: Int?,
)

data class SpecialEvent(
    val id: String,
    val titleEn: String,
    val titleZh: String?,
    val dateEn: String,
    val dateZh: String?,
    val showFullDate: Boolean,
    val world: String?,
    val friendIds: List<String>,
    val descriptionEn: String?,
    val descriptionZh: String?,
    val photoIds: List<Int>,
    val featuredPhotoIds: List<Int>,
)

data class GalleryRow(
    val photo: GalleryImage,
    val linkedPhotos: List<GalleryImage>,
)

data class SpecialEventView(
    val event: SpecialEvent,
    val photos: List<GalleryImage>,
    val featuredPhotos: List<GalleryImage>,
    val linkedPhotos: List<GalleryImage>,
    val sortKey: String,
)

sealed interface GalleryFlowItem {
    data class Rows(
        val id: String,
        val rows: List<GalleryRow>,
    ) : GalleryFlowItem

    data class Event(
        val event: SpecialEventView,
    ) : GalleryFlowItem
}

data class GalleryContent(
    val friends: List<Friend>,
    val worlds: List<World>,
    val photos: List<GalleryImage>,
    val rows: List<GalleryRow>,
    val specialEvents: List<SpecialEventView>,
    val flowItems: List<GalleryFlowItem>,
) {
    val friendsById = friends.associateBy { it.id }
    val worldsById = worlds.associateBy { it.id }
}

enum class AppLanguage {
    System,
    En,
    Zh,
}

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class GallerySettings(
    val language: AppLanguage,
    val themeMode: ThemeMode,
    val baseUrl: String,
)

sealed interface GalleryFilter {
    data class World(val id: String) : GalleryFilter
    data class Friend(val id: String) : GalleryFilter
}
