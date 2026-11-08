package com.github.devapro.pttdroid.data

import com.github.devapro.pttdroid.data.qr.QrMatrix
import com.github.devapro.pttdroid.data.settings.AppSettings
import com.github.devapro.pttdroid.data.settings.RelayLink
import com.github.devapro.pttdroid.data.settings.ServerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayLinkTest {

    private val pin = "AB".repeat(32)

    @Test
    fun `round trips every field`() {
        val link = RelayLink("relay.example.org", 8443, tls = true, pin = pin, token = "s3cr&t=/ü 1")
        assertEquals(link, RelayLink.parse(link.toUri()))
    }

    @Test
    fun `round trips ipv6 host and optional fields`() {
        val link = RelayLink("[::1]", 8000, tls = false)
        assertEquals(link, RelayLink.parse(link.toUri()))
    }

    @Test
    fun `omits empty pin and token from the uri`() {
        val uri = RelayLink("10.0.0.5", 8000, tls = false).toUri()
        assertEquals("pttdroid://relay?host=10.0.0.5&port=8000&tls=0", uri)
    }

    @Test
    fun `token is only shared when asked for`() {
        val settings = AppSettings(
            serverMode = ServerMode.CUSTOM, customHost = "relay.local", customPort = 8000,
            useTls = true, certificateSha256 = pin, accessToken = "secret",
        )
        assertEquals("", RelayLink.from(settings, includeToken = false)?.token)
        assertEquals("secret", RelayLink.from(settings, includeToken = true)?.token)
        assertEquals(pin, RelayLink.from(settings, includeToken = false)?.pin)
    }

    @Test
    fun `nothing to share on the default relay`() {
        assertNull(RelayLink.from(AppSettings(serverMode = ServerMode.DEFAULT), includeToken = true))
    }

    @Test
    fun `rejects foreign and malformed input`() {
        listOf(
            "",
            "https://relay.example.org",
            "pttdroid://other?host=a&port=1&tls=1",
            "pttdroid://relay?port=8000&tls=1",
            "pttdroid://relay?host=a.b&tls=1",
            "pttdroid://relay?host=a.b&port=0&tls=1",
            "pttdroid://relay?host=a.b&port=70000&tls=1",
            "pttdroid://relay?host=a.b&port=80&tls=2",
            "pttdroid://relay?host=a.b&port=80&tls=1&pin=XYZ",
            "pttdroid://relay?host=user%40a.b&port=80&tls=1",
            "pttdroid://relay?host=a.b%2Fpath&port=80&tls=1",
            "pttdroid://relay?host=wss%3A%2F%2Fa.b&port=80&tls=1",
            "pttdroid://relay?host=a.b&host=c.d&port=80&tls=1",
            "pttdroid://relay?host=a.b&port=80&tls=1&token=%ZZ",
            "pttdroid://relay?host=a.b&port=80&tls=1&token=" + "x".repeat(AppSettings.MAX_TOKEN_LENGTH + 1),
        ).forEach { assertNull(RelayLink.parse(it), "should reject: $it") }
    }

    @Test
    fun `accepts a pin written with separators`() {
        val spaced = pin.chunked(2).joinToString(":")
        val parsed = RelayLink.parse("pttdroid://relay?host=a.b&port=80&tls=1&pin=" + spaced.replace(":", "%3A"))
        assertEquals(pin, parsed?.pin)
    }

    @Test
    fun `a full link fits in a qr code`() {
        val uri = RelayLink("relay.example.org", 8443, true, pin, "t".repeat(AppSettings.MAX_TOKEN_LENGTH)).toUri()
        val matrix = assertNotNull(QrMatrix.encode(uri))
        assertTrue(matrix.size >= 21 && matrix.all { it.size == matrix.size })
    }
}
