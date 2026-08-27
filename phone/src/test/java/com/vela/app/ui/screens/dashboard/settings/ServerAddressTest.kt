package com.vela.app.ui.screens.dashboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerAddressTest {

    @Test
    fun composeHttpsOmitsDefaultPort() {
        assertEquals(
            "https://nas.example",
            composeServerUrl("nas.example", true, "443", "")
        )
    }

    @Test
    fun composeHttpKeepsJellyfinPort() {
        assertEquals(
            "http://192.168.0.1:8096",
            composeServerUrl("192.168.0.1", false, "8096", "")
        )
    }

    @Test
    fun composeNormalizesPathAndStripsSchemeFromHost() {
        assertEquals(
            "https://host/emby",
            composeServerUrl("https://host", true, "443", "emby")
        )
    }

    @Test
    fun defaultPortsMatchProtocol() {
        assertEquals("443", defaultPort(true))
        assertEquals("8096", defaultPort(false))
    }
}
