package com.jellycine.app.discord

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jellycine.player.discord.DiscordRpcManager
import kotlinx.coroutines.launch

class DiscordAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri != null && uri.scheme == "jellycine") {
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
        } else {
            val manager = DiscordRpcManager.getInstance(this)
            val authUrl = manager.getAuthorizationUrl()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
            finish()
        }
    }
}
