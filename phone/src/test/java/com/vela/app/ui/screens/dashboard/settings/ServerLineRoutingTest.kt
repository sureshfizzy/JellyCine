package com.vela.app.ui.screens.dashboard.settings

import com.vela.data.network.NetworkAccess
import com.vela.data.network.RoutableLine
import com.vela.data.network.hostFromUrl
import com.vela.data.network.isLanHost
import com.vela.data.network.pickPreferredReachableLine
import com.vela.data.network.preferLan
import com.vela.data.network.requestMatchesServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerLineRoutingTest {

    private val lan = RoutableLine(id = "lan", isLan = true)
    private val wan = RoutableLine(id = "wan", isLan = false)

    @Test
    fun lanHostsCoverPrivateRangesAndLocalNames() {
        assertTrue(isLanHost("192.168.1.8"))
        assertTrue(isLanHost("10.0.0.2"))
        assertTrue(isLanHost("172.16.0.1"))
        assertTrue(isLanHost("localhost"))
        assertTrue(isLanHost("nas.local"))
        assertTrue(isLanHost("media.lan"))
        assertFalse(isLanHost("172.15.0.1"))
        assertFalse(isLanHost("8.8.8.8"))
        assertFalse(isLanHost("jellyfin.example.com"))
    }

    @Test
    fun hostFromUrlStripsPortAndPath() {
        assertEquals("192.168.0.21", hostFromUrl("http://192.168.0.21:8096/emby"))
        assertEquals("nas.local", hostFromUrl("https://nas.local/jellyfin"))
        assertEquals("::1", hostFromUrl("http://[::1]:8096"))
    }

    @Test
    fun wifiPrefersLanWhenLanIsReachable() {
        val picked = pickPreferredReachableLine(
            lines = listOf(wan, lan),
            reachableIds = setOf("lan", "wan"),
            preferLan = true,
            currentId = "wan"
        )
        assertEquals("lan", picked?.id)
    }

    @Test
    fun cellularPrefersWanWhenWanIsReachable() {
        val picked = pickPreferredReachableLine(
            lines = listOf(lan, wan),
            reachableIds = setOf("lan", "wan"),
            preferLan = false,
            currentId = "lan"
        )
        assertEquals("wan", picked?.id)
    }

    @Test
    fun wifiFallsBackToWanWhenLanProbeFails() {
        val picked = pickPreferredReachableLine(
            lines = listOf(lan, wan),
            reachableIds = setOf("wan"),
            preferLan = true,
            currentId = "lan"
        )
        assertEquals("wan", picked?.id)
    }

    @Test
    fun staysOnCurrentWhenItAlreadyMatchesPreference() {
        val picked = pickPreferredReachableLine(
            lines = listOf(lan, wan),
            reachableIds = setOf("lan", "wan"),
            preferLan = true,
            currentId = "lan"
        )
        assertEquals("lan", picked?.id)
    }

    @Test
    fun failoverSkipsUnreachableCurrentLine() {
        val picked = pickPreferredReachableLine(
            lines = listOf(lan, wan),
            reachableIds = setOf("wan"),
            preferLan = true,
            currentId = "lan"
        )
        assertEquals("wan", picked?.id)
    }

    @Test
    fun returnsNullWhenNothingIsReachable() {
        assertNull(
            pickPreferredReachableLine(
                lines = listOf(lan, wan),
                reachableIds = emptySet(),
                preferLan = true,
                currentId = "lan"
            )
        )
    }

    @Test
    fun wifiIsLanCapableAndCellularIsNot() {
        assertTrue(preferLan(NetworkAccess.LAN_CAPABLE))
        assertFalse(preferLan(NetworkAccess.WAN))
        assertFalse(preferLan(NetworkAccess.OFFLINE))
    }

    @Test
    fun requestMatchesServerUrlIgnoresApiPath() {
        assertTrue(
            requestMatchesServerUrl(
                "http://192.168.0.21:8096/emby/Users/abc/Items",
                "http://192.168.0.21:8096/emby"
            )
        )
        assertFalse(
            requestMatchesServerUrl(
                "https://wan.example/emby/Users/abc/Items",
                "http://192.168.0.21:8096/emby"
            )
        )
    }
}
