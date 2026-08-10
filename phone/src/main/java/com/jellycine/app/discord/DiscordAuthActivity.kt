package com.jellycine.app.discord

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.jellycine.player.discord.DiscordRpcManager
import kotlinx.coroutines.launch

class DiscordAuthActivity : ComponentActivity() {

    private var hasResumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (handleCallbackIfPresent(intent)) return

        if (savedInstanceState == null) {
            openAuthFlow()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallbackIfPresent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            if (intent?.data?.scheme != "jellycine") {
                finish()
            }
        }
        hasResumedOnce = true
    }

    private fun handleCallbackIfPresent(intent: android.content.Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "jellycine") return false
        val code = uri.getQueryParameter("code")
        if (code != null) {
            val manager = DiscordRpcManager.getInstance(this)
            lifecycleScope.launch {
                manager.exchangeCodeForToken(code)
                finish()
            }
        } else {
            finish()
        }
        return true
    }

    private fun openAuthFlow() {
        val manager = DiscordRpcManager.getInstance(this)
        val authUrl = manager.getAuthorizationUrl()
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, Uri.parse(authUrl))
    }
}
