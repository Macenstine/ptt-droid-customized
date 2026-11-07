package com.github.devapro.pttdroid.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearStateTest {

    @Test
    fun `round trips with a floor holder`() {
        val state = WearState(WearStatus.RECEIVING, channel = 7, peers = 3, floorHolderName = "Mira", hasError = false)
        assertEquals(state, WearState.decode(state.encode()))
    }

    @Test
    fun `round trips without a floor holder`() {
        val state = WearState(WearStatus.READY, channel = 1, peers = 0, floorHolderName = null, hasError = true)
        assertEquals(state, WearState.decode(state.encode()))
    }

    @Test
    fun `unreadable payloads decode to null`() {
        assertNull(WearState.decode(null))
        assertNull(WearState.decode(byteArrayOf()))
        assertNull(WearState.decode(byteArrayOf(99, 0, 0)))
        assertNull(WearState.decode(WearState(WearStatus.READY, 1, 0, null, false).encode().copyOf(4)))
    }

    @Test
    fun `unknown status codes fall back to offline`() {
        assertEquals(WearStatus.OFFLINE, WearStatus.fromCode(250))
    }

    @Test
    fun `status codes are unique`() {
        assertEquals(WearStatus.entries.size, WearStatus.entries.map { it.code }.toSet().size)
    }

    @Test
    fun `channel payload round trips`() {
        for (channel in listOf(1, 7, 99, 300, -1)) {
            assertEquals(channel, ChannelPayload.decode(ChannelPayload.encode(channel)))
        }
        assertNull(ChannelPayload.decode(byteArrayOf(1, 2)))
        assertNull(ChannelPayload.decode(null))
    }
}
