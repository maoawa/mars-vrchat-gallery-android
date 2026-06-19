package cc.maao.vrchat.data

import android.content.Context

class GallerySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "gallery_settings",
        Context.MODE_PRIVATE,
    )

    fun read(): GallerySettings {
        return GallerySettings(
            language = preferences.getString(KEY_LANGUAGE, null).toLanguage(),
            themeMode = preferences.getString(KEY_THEME, null).toThemeMode(),
            baseUrl = preferences.getString(KEY_BASE_URL, null).toBaseUrl(),
        )
    }

    fun save(settings: GallerySettings) {
        preferences.edit()
            .putString(KEY_LANGUAGE, settings.language.name)
            .putString(KEY_THEME, settings.themeMode.name)
            .putString(KEY_BASE_URL, settings.baseUrl)
            .apply()
    }

    private fun String?.toLanguage(): AppLanguage {
        return AppLanguage.entries.firstOrNull { it.name == this } ?: AppLanguage.System
    }

    private fun String?.toThemeMode(): ThemeMode {
        return ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.System
    }

    private fun String?.toBaseUrl(): String {
        return if (this in BASE_URLS) this!! else DEFAULT_BASE_URL
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://vrchat.marsinside.com"
        val BASE_URLS = listOf(DEFAULT_BASE_URL, "https://vrchat.maao.cc")

        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_BASE_URL = "base_url"
    }
}
