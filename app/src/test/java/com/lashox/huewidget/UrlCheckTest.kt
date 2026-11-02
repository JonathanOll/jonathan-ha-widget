package com.lashox.huewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCheckTest {

    private fun local(vararg hosts: String) = hosts.forEach { assertTrue("expected local: $it", isLocalHost(it)) }
    private fun public(vararg hosts: String) = hosts.forEach { assertFalse("expected public: $it", isLocalHost(it)) }

    // ---- isLocalHost: IPv4 ----

    @Test fun ipv4_10slash8() {
        local("10.0.0.0", "10.0.0.1", "10.255.255.255")
        public("11.0.0.1", "9.255.255.255")
    }

    @Test fun ipv4_172_16slash12_boundaries() {
        public("172.15.255.255", "172.15.0.1")
        local("172.16.0.0", "172.16.0.1", "172.20.1.1", "172.31.255.255")
        public("172.32.0.0", "172.32.0.1")
    }

    @Test fun ipv4_192_168slash16() {
        local("192.168.0.1", "192.168.1.10", "192.168.255.255")
        public("192.167.1.1", "192.169.1.1")
    }

    @Test fun ipv4_loopbackAndLinkLocal() {
        local("127.0.0.1", "127.1.2.3", "169.254.0.1", "169.254.255.255")
        public("169.253.1.1", "169.255.1.1", "128.0.0.1")
    }

    @Test fun ipv4_public() {
        public("8.8.8.8", "1.1.1.1", "100.64.0.1", "0.0.0.0", "255.255.255.255")
    }

    @Test fun ipv4_malformedIsNotTreatedAsIp() {
        // not 4 valid octets -> parsed as a name -> not a local suffix -> public
        public("192.168.1", "192.168.1.256", "192.168.1.1.1", "192.168.-1.1")
    }

    // ---- isLocalHost: IPv6 ----

    @Test fun ipv6_local() {
        local("::1", "[::1]", "fe80::1", "[fe80::1]", "febf::1", "fd00::1", "fd12:3456:789a::1", "fc00::1",
            "::ffff:192.168.1.2", "[::ffff:192.168.1.2]", "::ffff:10.0.0.1", "::ffff:c0a8:0102", "FE80::ABCD",
            "fe80::1%wlan0")
    }

    @Test fun ipv6_public() {
        public("::", "2001:db8::1", "2a00:1450:4007:80b::200e", "fec0::1", "fe00::1",
            "::ffff:8.8.8.8", "::ffff:172.32.0.1", "::2")
    }

    @Test fun ipv6_malformed() {
        public("1::2::3", "gggg::1", "12345::1", "1:2:3:4:5:6:7:8:9", "fe80:")
    }

    // ---- isLocalHost: names ----

    @Test fun names_local() {
        local("localhost", "LOCALHOST", "localhost.", "homeassistant.local", "ha.lan", "ha.home.arpa",
            "homeassistant.local.", "foo.localhost")
    }

    @Test fun names_public() {
        public("example.com", "ha.duckdns.org", "local", "lan", "mylocal", "evil-lan", "home.arpa.example.com",
            "192.168.1.10.nip.io", "")
    }

    // ---- checkHaUrl ----

    @Test fun checkHaUrl_httpLocalOk() {
        for (u in listOf(
            "http://192.168.1.10:8123", "http://192.168.1.10:8123/", "  http://192.168.1.10:8123  ",
            "http://homeassistant.local:8123", "http://localhost:8123", "http://127.0.0.1",
            "http://[::1]:8123", "http://[fd00::1]:8123", "http://[::ffff:192.168.1.2]:8123", "HTTP://10.0.0.2"
        )) assertEquals(u, UrlCheck.OK, checkHaUrl(u))
    }

    @Test fun checkHaUrl_httpsAlwaysOk() {
        for (u in listOf("https://ha.example.com", "https://8.8.8.8:8123", "https://192.168.1.10:8123"))
            assertEquals(u, UrlCheck.OK, checkHaUrl(u))
    }

    @Test fun checkHaUrl_httpPublicRefused() {
        for (u in listOf(
            "http://ha.example.com", "http://8.8.8.8:8123", "http://172.32.0.1:8123",
            "http://[2001:db8::1]:8123", "http://192.168.1.10.nip.io"
        )) assertEquals(u, UrlCheck.INSECURE_PUBLIC, checkHaUrl(u))
    }

    @Test fun checkHaUrl_invalid() {
        for (u in listOf(
            "", "   ", "192.168.1.10:8123", "localhost:8123", "htp://192.168.1.10", "ftp://192.168.1.10",
            "http://", "https://", "http:///api", "file:///etc/hosts", "not a url"
        )) assertEquals("'$u'", UrlCheck.INVALID, checkHaUrl(u))
    }
}
