package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val hintsEnabled: Boolean = true,
    /** Avatars the player has already seen unlocked (drives the "NEW" badge). */
    val seenAvatarIds: Set<String> = emptySet()
)

/** Device-local settings (SharedPreferences), readable from game logic and UI. */
object SettingsStore {
    private const val PREFS = "wastecrush_settings"
    private const val KEY_SOUND = "sound"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_HINTS = "hints"
    private const val KEY_SEEN_AVATARS = "seen_avatars"

    private var appContext: Context? = null
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = prefs() ?: return
        _settings.value = AppSettings(
            soundEnabled = p.getBoolean(KEY_SOUND, true),
            hapticsEnabled = p.getBoolean(KEY_HAPTICS, true),
            hintsEnabled = p.getBoolean(KEY_HINTS, true),
            seenAvatarIds = p.getStringSet(KEY_SEEN_AVATARS, emptySet())?.toSet() ?: emptySet()
        )
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setSound(enabled: Boolean) = update(_settings.value.copy(soundEnabled = enabled))
    fun setHaptics(enabled: Boolean) = update(_settings.value.copy(hapticsEnabled = enabled))
    fun setHints(enabled: Boolean) = update(_settings.value.copy(hintsEnabled = enabled))
    fun markAvatarsSeen(ids: Collection<String>) {
        val merged = _settings.value.seenAvatarIds + ids
        if (merged != _settings.value.seenAvatarIds) update(_settings.value.copy(seenAvatarIds = merged))
    }

    private fun update(next: AppSettings) {
        _settings.value = next
        prefs()?.edit()
            ?.putBoolean(KEY_SOUND, next.soundEnabled)
            ?.putBoolean(KEY_HAPTICS, next.hapticsEnabled)
            ?.putBoolean(KEY_HINTS, next.hintsEnabled)
            ?.putStringSet(KEY_SEEN_AVATARS, next.seenAvatarIds)
            ?.apply()
    }
}
