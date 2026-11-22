package app.smartnet

import android.content.Context

object SmartNetPrefs {
    private const val FILE = "smart_net_preferences"
    private const val FAVORITES = "favorite_components"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getFavorites(context: Context): MutableSet<String> =
        prefs(context).getStringSet(FAVORITES, null)?.toMutableSet() ?: linkedSetOf(
            "com.whatsapp/com.whatsapp.Main",
            "com.google.android.youtube/com.google.android.youtube.HomeActivity",
            "com.android.chrome/com.google.android.apps.chrome.Main",
            "com.spotify.music/com.spotify.music.MainActivity",
            "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity",
            "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
        )

    fun saveFavorites(context: Context, values: Set<String>) {
        prefs(context).edit().putStringSet(FAVORITES, values.toSet()).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean(key, default)

    fun putBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int): Int =
        prefs(context).getInt(key, default)

    fun putInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun getString(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }
}
