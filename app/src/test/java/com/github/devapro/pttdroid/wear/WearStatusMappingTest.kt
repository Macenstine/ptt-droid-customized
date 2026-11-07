package com.github.devapro.pttdroid.wear

import com.github.devapro.pttdroid.domain.ConnectionStatus
import com.github.devapro.pttdroid.domain.PttState
import com.github.devapro.pttdroid.ui.PttUiStatus
import com.github.devapro.pttdroid.wear.protocol.WearStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearStatusMappingTest {

    @Test
    fun `every phone status has a watch status with the same colour and control rule`() {
        for (phone in PttUiStatus.entries) {
            val watch = phone.toWearStatus()
            assertEquals("$phone colour", phone.argb, watch.argb)
            assertEquals("$phone control", phone.isControlLive, watch.isControlLive)
        }
    }

    @Test
    fun `the mapping is one to one`() {
        assertEquals(PttUiStatus.entries.size, PttUiStatus.entries.map { it.toWearStatus() }.toSet().size)
        assertEquals(WearStatus.entries.size, PttUiStatus.entries.size)
    }

    @Test
    fun `state carries the floor holder only while someone else holds it`() {
        val connected = PttState(status = ConnectionStatus.Connected, channel = 4, peers = 2)

        val busy = connected.copy(isFloorHeldByOther = true, floorHolderName = "Mira").toWearState()
        assertEquals(WearStatus.RECEIVING, busy.status)
        assertEquals("Mira", busy.floorHolderName)

        val ours = connected.copy(isTransmitting = true, floorHolderName = "Me").toWearState()
        assertEquals(WearStatus.TRANSMITTING, ours.status)
        assertNull(ours.floorHolderName)
        assertEquals(4, ours.channel)
        assertEquals(2, ours.peers)
    }

    @Test
    fun `an error is flagged without its text`() {
        assertEquals(true, PttState(lastError = "boom").toWearState().hasError)
        assertEquals(false, PttState().toWearState().hasError)
    }
}
