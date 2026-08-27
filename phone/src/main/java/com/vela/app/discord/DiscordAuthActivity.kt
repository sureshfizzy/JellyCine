package com.vela.app.discord

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.vela.player.discord.DiscordRpcManager
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
            if (intent?.data?.scheme != "vela") {
                finish()
            }
        }
        hasResumedOnce = true
    }

    private fun handleCallbackIfPresent(intent: android.content.Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "vela") return false
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
        val uri = Uri.parse(authUrl)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, uri)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
