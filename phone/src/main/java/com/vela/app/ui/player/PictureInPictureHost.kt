package com.vela.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.widget.Toast
import com.vela.shared.R
import kotlinx.coroutines.flow.StateFlow

interface PictureInPictureHost {
    val pictureInPictureMode: StateFlow<Boolean>
    var userLeaveHintHandler: (() -> Unit)?
}

fun enterPlayerPip(activity: Activity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        Toast.makeText(activity, activity.getString(R.string.player_pip_failed), Toast.LENGTH_SHORT).show()
        return false
    }
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        Toast.makeText(activity, activity.getString(R.string.player_pip_failed), Toast.LENGTH_SHORT).show()
        return false
    }
    if (activity.isInPictureInPictureMode) return true
    val entered = try {
        activity.enterPictureInPictureMode(playerPipParams(autoEnter = false))
    } catch (_: RuntimeException) {
        false
    }
    if (!entered) {
        Toast.makeText(activity, activity.getString(R.string.player_pip_failed), Toast.LENGTH_SHORT).show()
    }
    return entered
}

fun applyPlayerPipParams(activity: Activity, playing: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    try {
        activity.setPictureInPictureParams(playerPipParams(autoEnter = playing))
    } catch (_: RuntimeException) {
        // 部分机型在非播放 Activity 上拒绝更新 PiP 参数。
    }
}

private fun playerPipParams(autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        builder.setSeamlessResizeEnabled(true)
    }
    return builder.build()
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
