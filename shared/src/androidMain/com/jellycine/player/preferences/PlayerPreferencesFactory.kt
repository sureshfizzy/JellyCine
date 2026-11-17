package com.jellycine.player.preferences

import android.content.Context
import com.jellycine.preferences.SharedPreferencesStore

fun PlayerPreferences(context: Context): PlayerPreferences {
    return PlayerPreferences(
        SharedPreferencesStore(context.applicationContext, PlayerPreferences.PREFS_NAME)
    )
}
