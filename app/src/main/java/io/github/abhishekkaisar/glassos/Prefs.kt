package io.github.abhishekkaisar.glassos

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) { sp.edit().putBoolean("sound", v).apply() }

    /**
     * Fallback background, shown when the Top Shelf has no title art: a built-in scene (scene:..., default),
     * the user's own photos in Pictures/Glass (hero:photos) or one image (file:...).
     */
    var scene: String
        get() = sp.getString("fallback", DEFAULT_SCENE) ?: DEFAULT_SCENE
        set(v) { sp.edit().putString("fallback", v).apply() }

    /** Top Shelf source: [HeroRepo.SRC_TMDB] (default), [HeroRepo.SRC_APPLE] or [HeroRepo.SRC_SCENES]. */
    var topShelf: String
        get() = sp.getString("topShelf", HeroRepo.SRC_TMDB) ?: HeroRepo.SRC_TMDB
        set(v) { sp.edit().putString("topShelf", v).apply() }

    /** TMDB key entered in Settings; the build's own key (BuildConfig.TMDB_API_KEY) is used when this is empty. */
    var tmdbKey: String
        get() = sp.getString("tmdbKey", "") ?: ""
        set(v) { sp.edit().putString("tmdbKey", v.trim()).apply() }

    val effectiveTmdbKey: String get() = tmdbKey.ifEmpty { BuildConfig.TMDB_API_KEY }

    /** ISO 3166 country code for the Top Shelf region (streaming availability). */
    var country: String
        get() = sp.getString("country", "") ?: ""
        set(v) { sp.edit().putString("country", v).apply() }

    var replaceHome: Boolean
        get() = sp.getBoolean("replaceHome", true)
        set(v) { sp.edit().putBoolean("replaceHome", v).apply() }

    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) { sp.edit().putString("dock", v?.joinToString(",")).apply() }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) { sp.edit().putBoolean("setupDone", v).apply() }

    var askedStorage: Boolean
        get() = sp.getBoolean("askedStorage", false)
        set(v) { sp.edit().putBoolean("askedStorage", v).apply() }

    companion object {
        const val DEFAULT_SCENE = "scene:desert"
    }
}
