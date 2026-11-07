package com.github.devapro.pttdroid.wear.protocol

/**
 * What the watch shows, mirroring the phone's `PttUiStatus` one for one.
 *
 * The watch cannot depend on `:shared`, so the colours are repeated here as raw ARGB; a test in
 * `:app` asserts they stay equal to `PttUiStatus`, because a colour has to mean the same thing
 * on every surface. [code] is what travels on the wire, so reordering the enum cannot change it.
 */
enum class WearStatus(val code: Int, val argb: Long, val word: String) {
    OFFLINE(0, 0xFF64748B, "OFFLINE"),
    CONNECTING(1, 0xFFF59E0B, "LINKING"),
    READY(2, 0xFF22C55E, "HOLD"),
    REQUESTING(3, 0xFFF59E0B, "WAIT"),
    TRANSMITTING(4, 0xFFEF4444, "ON AIR"),
    RECEIVING(5, 0xFF38BDF8, "BUSY"),
    ;

    /**
     * The button must stay live while we hold the floor or are waiting for it, so the release
     * gesture is never dropped (the same rule as `PttUiStatus.isControlLive`).
     */
    val isControlLive: Boolean
        get() = this == READY || this == REQUESTING || this == TRANSMITTING

    companion object {
        fun fromCode(code: Int): WearStatus = entries.firstOrNull { it.code == code } ?: OFFLINE
    }
}
