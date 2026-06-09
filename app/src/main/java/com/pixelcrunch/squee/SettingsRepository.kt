package com.pixelcrunch.squee

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class OutputFormat(val label: String, val extension: String) {
    JPEG("JPEG", "jpg"),
    WEBP("WebP", "webp");

    companion object {
        fun fromName(name: String): OutputFormat =
            entries.firstOrNull { it.name == name } ?: WEBP
    }
}

enum class ResolutionPreset(val label: String, val maxDimension: Int) {
    RES_2160P("2160p (4K)", 3840),
    RES_1440P("1440p", 2560),
    RES_1080P("1080p", 1920),
    RES_720P("720p", 1280),
    RES_480P("480p", 854);
}

data class UserPreferences(
    val resolutionPreset: ResolutionPreset = ResolutionPreset.RES_1080P,
    val compressionQuality: Int = 75,
    val stripMetadata: Boolean = true,
    val outputFormat: OutputFormat = OutputFormat.WEBP,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val RESOLUTION_PRESET = stringPreferencesKey("resolution_preset")
        val COMPRESSION_QUALITY = intPreferencesKey("compression_quality")
        val STRIP_METADATA = booleanPreferencesKey("strip_metadata")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            resolutionPreset = prefs[Keys.RESOLUTION_PRESET]?.let { name ->
                ResolutionPreset.entries.firstOrNull { it.name == name }
            } ?: ResolutionPreset.RES_1080P,
            compressionQuality = prefs[Keys.COMPRESSION_QUALITY] ?: 75,
            stripMetadata = prefs[Keys.STRIP_METADATA] ?: true,
            outputFormat = prefs[Keys.OUTPUT_FORMAT]?.let { OutputFormat.fromName(it) }
                ?: OutputFormat.WEBP,
        )
    }

    suspend fun updateResolution(preset: ResolutionPreset) {
        context.dataStore.edit { it[Keys.RESOLUTION_PRESET] = preset.name }
    }

    suspend fun updateQuality(quality: Int) {
        context.dataStore.edit { it[Keys.COMPRESSION_QUALITY] = quality.coerceIn(1, 100) }
    }

    suspend fun updateStripMetadata(strip: Boolean) {
        context.dataStore.edit { it[Keys.STRIP_METADATA] = strip }
    }

    suspend fun updateOutputFormat(format: OutputFormat) {
        context.dataStore.edit { it[Keys.OUTPUT_FORMAT] = format.name }
    }
}
