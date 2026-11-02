package com.mauyaapps.aircode.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestNetworkTest {

    @Test
    fun androidServersComeFirstThenFallbacks() {
        val text = GuestNetwork.resolvConf(listOf("192.168.1.1"))
        assertEquals(
            listOf("nameserver 192.168.1.1", "nameserver 1.1.1.1", "nameserver 8.8.8.8"),
            text.lines().filter { it.startsWith("nameserver") },
        )
    }

    @Test
    fun atMostThreeNameserversAndNoDuplicates() {
        val text = GuestNetwork.resolvConf(listOf("10.0.0.1", "10.0.0.2", "10.0.0.3", "1.1.1.1"))
        assertEquals(
            listOf("nameserver 10.0.0.1", "nameserver 10.0.0.2", "nameserver 1.1.1.1"),
            text.lines().filter { it.startsWith("nameserver") },
        )
    }

    @Test
    fun loopbackAndLinkLocalAreDropped() {
        val text = GuestNetwork.resolvConf(listOf("127.0.0.1", "fe80::1%wlan0", "::1", ""))
        assertEquals(
            listOf("nameserver 1.1.1.1", "nameserver 8.8.8.8"),
            text.lines().filter { it.startsWith("nameserver") },
        )
    }

    @Test
    fun scopeSuffixIsStrippedFromGlobalAddresses() {
        assertTrue(GuestNetwork.resolvConf(listOf("2001:db8::1%eth0")).startsWith("nameserver 2001:db8::1\n"))
    }

    @Test
    fun hostsFileHasLocalhostAndPins() {
        val text = GuestNetwork.hostsFile(mapOf("dl-cdn.alpinelinux.org" to listOf("151.101.2.132", "2a04::1")))
        assertTrue(text.startsWith("127.0.0.1 localhost"))
        assertTrue("151.101.2.132 dl-cdn.alpinelinux.org\n" in text)
        assertTrue("2a04::1 dl-cdn.alpinelinux.org\n" in text)
        assertEquals(GuestNetwork.hostsFile(), GuestNetwork.hostsFile(emptyMap()))
    }

    @Test
    fun repositoriesSwitchToHttp() {
        val result = GuestNetwork.httpRepositories("https://dl-cdn.alpinelinux.org/alpine/v3.22/main\n#https://x/y\nhttp://a/b\n")
        assertEquals("http://dl-cdn.alpinelinux.org/alpine/v3.22/main\n#https://x/y\nhttp://a/b\n", result)
    }
}
