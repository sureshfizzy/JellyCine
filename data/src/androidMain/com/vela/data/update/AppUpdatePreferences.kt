package com.vela.data.update

import android.content.Context

class AppUpdatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMirrorId(): String {
        return prefs.getString(KEY_MIRROR_ID, DEFAULT_DOWNLOAD_MIRROR_ID) ?: DEFAULT_DOWNLOAD_MIRROR_ID
    }

    fun getCustomPrefix(): String {
        return prefs.getString(KEY_CUSTOM_PREFIX, "") ?: ""
    }

    fun getMirror(): DownloadMirror {
        return resolveDownloadMirror(getMirrorId(), getCustomPrefix())
    }

    fun setMirrorId(id: String) {
        prefs.edit().putString(KEY_MIRROR_ID, id).apply()
    }

    fun setCustomPrefix(prefix: String) {
        val sanitized = sanitizeMirrorPrefix(prefix) ?: return
        prefs.edit()
            .putString(KEY_CUSTOM_PREFIX, sanitized)
            .putString(KEY_MIRROR_ID, CUSTOM_DOWNLOAD_MIRROR_ID)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "vela_app_update_prefs"
        private const val KEY_MIRROR_ID = "download_mirror_id"
        private const val KEY_CUSTOM_PREFIX = "download_mirror_custom_prefix"
    }
}
