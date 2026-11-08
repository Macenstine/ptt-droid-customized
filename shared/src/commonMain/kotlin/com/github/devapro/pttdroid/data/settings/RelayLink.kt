package com.github.devapro.pttdroid.data.settings

/**
 * A relay connection, packed into one URI so it can travel through a QR code (or, later, an NFC
 * tag): `pttdroid://relay?host=relay.example&port=8000&tls=1&pin=AB12…&token=secret`.
 *
 * Only what is needed to *connect* is carried — personal settings such as display name or channel
 * are not. [pin] is a normalized SHA-256 fingerprint ([CertificatePin]) or empty; [token] is empty
 * unless the sharer explicitly chose to include it, because a code that carries it grants access
 * to anyone who can see the code.
 *
 * Whatever [parse] returns is data from an untrusted source (a printed code can say anything), so
 * it is validated here with the same rules as typed input, and the caller is expected to show it
 * for confirmation rather than apply it silently.
 */
data class RelayLink(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val pin: String = "",
    val token: String = "",
) {
    /** The URI form, as put in a QR code. */
    fun toUri(): String = buildString {
        append(PREFIX)
        append("host=").append(encode(host))
        append("&port=").append(port)
        append("&tls=").append(if (tls) "1" else "0")
        if (pin.isNotEmpty()) append("&pin=").append(encode(pin))
        if (token.isNotEmpty()) append("&token=").append(encode(token))
    }

    companion object {
        private const val PREFIX = "pttdroid://relay?"
        private const val UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private const val HEX = "0123456789ABCDEF"

        /** The link for a saved custom relay, or null while the app is on its built-in default. */
        fun from(settings: AppSettings, includeToken: Boolean): RelayLink? {
            if (!settings.serverMode.isCustom) return null
            val host = settings.customHost.trim()
            if (host.isEmpty()) return null
            return RelayLink(
                host = host,
                port = settings.customPort,
                tls = settings.useTls,
                pin = CertificatePin.normalize(settings.certificateSha256),
                token = if (includeToken) settings.accessToken else "",
            )
        }

        /** Null for anything that is not a well-formed, valid relay link. */
        fun parse(raw: String): RelayLink? {
            val text = raw.trim()
            if (!text.startsWith(PREFIX, ignoreCase = true)) return null
            val params = HashMap<String, String>()
            for (pair in text.substring(PREFIX.length).split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq < 0) return null
                val key = pair.take(eq)
                if (key in params) return null
                params[key] = decode(pair.substring(eq + 1)) ?: return null
            }

            val host = params["host"] ?: return null
            val port = params["port"]?.takeIf { p -> p.isNotEmpty() && p.all { it.isDigit() } }
                ?.toIntOrNull()?.takeIf { it in AppSettings.PORT_RANGE } ?: return null
            val tls = when (params["tls"]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            // Same rules as the address box: rejects credentials, paths, schemes and junk.
            val address = ServerAddress.parse("$host:$port") as? ServerAddress.Valid ?: return null
            if (address.host != host || address.port != port) return null

            val pinText = params["pin"].orEmpty()
            val pin = CertificatePin.normalize(pinText)
            if (pinText.isNotEmpty() && pin.isEmpty()) return null

            val token = params["token"].orEmpty()
            if (token.length > AppSettings.MAX_TOKEN_LENGTH) return null

            return RelayLink(host = host, port = port, tls = tls, pin = pin, token = token)
        }

        private fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val v = byte.toInt() and 0xFF
                if (v < 0x80 && v.toChar() in UNRESERVED) {
                    append(v.toChar())
                } else {
                    append('%').append(HEX[v shr 4]).append(HEX[v and 0x0F])
                }
            }
        }

        private fun decode(value: String): String? {
            val out = ByteArray(value.length * 4)
            var n = 0
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '%') {
                    if (i + 3 > value.length) return null
                    val hi = HEX.indexOf(value[i + 1].uppercaseChar())
                    val lo = HEX.indexOf(value[i + 2].uppercaseChar())
                    if (hi < 0 || lo < 0) return null
                    out[n++] = ((hi shl 4) or lo).toByte()
                    i += 3
                } else {
                    for (b in c.toString().encodeToByteArray()) out[n++] = b
                    i++
                }
            }
            return try {
                out.copyOf(n).decodeToString(throwOnInvalidSequence = true)
            } catch (e: Exception) {
                null
            }
        }
    }
}
