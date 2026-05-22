package com.jellycine.data.download

import android.content.Context
import com.jellycine.data.R

class DownloadMessages(context: Context) {
    private val appContext = context.applicationContext

    val wifiRequired: String
        get() = appContext.getString(R.string.download_error_wifi_required)

    val storageShortageDevice: String
        get() = appContext.getString(R.string.download_error_storage_shortage_device)

    val notEnoughStorage: String
        get() = appContext.getString(R.string.download_error_not_enough_storage)

    val noEpisodesFoundForSeries: String
        get() = appContext.getString(R.string.download_error_no_episodes_found_series)

    val noEpisodesFoundToDownload: String
        get() = appContext.getString(R.string.download_error_no_episodes_found_to_download)

    val noEpisodesSelected: String
        get() = appContext.getString(R.string.download_error_no_episodes_selected)

    val noEpisodesQueued: String
        get() = appContext.getString(R.string.download_error_no_episodes_queued)

    val resuming: String
        get() = appContext.getString(R.string.download_status_resuming)

    val downloadFailed: String
        get() = appContext.getString(R.string.download_error_download_failed)

    val downloadIncomplete: String
        get() = appContext.getString(R.string.download_error_incomplete)

    val deleteFileFailed: String
        get() = appContext.getString(R.string.download_error_delete_file_failed)

    val itemIdUnavailable: String
        get() = appContext.getString(R.string.download_error_item_id_unavailable)

    val prepareDownloadFailed: String
        get() = appContext.getString(R.string.download_error_prepare_failed)

    fun itemFallbackTitle(itemId: String): String {
        return appContext.getString(R.string.download_item_fallback, itemId)
    }

    fun serverError(code: Int): String {
        return appContext.getString(R.string.download_error_server, code)
    }
}