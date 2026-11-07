package com.github.devapro.pttdroid.wear.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** The slice of the phone's `PttState` the watch needs to draw its one screen. */
data class WearState(
    val status: WearStatus,
    val channel: Int,
    val peers: Int,
    /** Who holds the floor, when it is somebody else. */
    val floorHolderName: String?,
    /** The phone has an error to show; the detail stays on the phone. */
    val hasError: Boolean,
) {
    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeByte(status.code)
            out.writeInt(channel)
            out.writeInt(peers)
            out.writeBoolean(hasError)
            out.writeBoolean(floorHolderName != null)
            if (floorHolderName != null) out.writeUTF(floorHolderName)
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val VERSION = 1

        /** Null for anything this build cannot read, so a newer phone cannot crash an older watch. */
        fun decode(data: ByteArray?): WearState? {
            if (data == null) return null
            return try {
                DataInputStream(ByteArrayInputStream(data)).use { input ->
                    if (input.readUnsignedByte() != VERSION) return null
                    val status = WearStatus.fromCode(input.readUnsignedByte())
                    val channel = input.readInt()
                    val peers = input.readInt()
                    val hasError = input.readBoolean()
                    val holder = if (input.readBoolean()) input.readUTF() else null
                    WearState(status, channel, peers, holder, hasError)
                }
            } catch (_: IOException) {
                null
            }
        }
    }
}

/** The 4-byte payload of a [WearPaths.CHANNEL] message. */
object ChannelPayload {
    fun encode(channel: Int): ByteArray = byteArrayOf(
        (channel ushr 24).toByte(),
        (channel ushr 16).toByte(),
        (channel ushr 8).toByte(),
        channel.toByte(),
    )

    fun decode(data: ByteArray?): Int? {
        if (data == null || data.size != 4) return null
        return data.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
    }
}
