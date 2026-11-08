package com.github.devapro.pttdroid.data.qr

import qrcode.QRCode
import qrcode.raw.ErrorCorrectionLevel

/**
 * A QR code as a square grid of modules, `true` for a dark one, indexed `[row][column]`.
 *
 * Returns the bare modules with no quiet zone: the view draws the margin, so the encoder stays
 * independent of how (or on what background) the code is painted. Null when [text] is too long
 * for any QR version, which the caller treats as "nothing to show".
 */
object QrMatrix {
    fun encode(text: String): List<List<Boolean>>? = try {
        QRCode(text, errorCorrectionLevel = ErrorCorrectionLevel.MEDIUM).rawData
            .map { row -> row.map { it.dark } }
    } catch (e: Exception) {
        null
    }
}
