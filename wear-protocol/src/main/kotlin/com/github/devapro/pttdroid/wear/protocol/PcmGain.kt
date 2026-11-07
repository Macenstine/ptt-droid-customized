package com.github.devapro.pttdroid.wear.protocol

/**
 * Software gain for PCM16LE audio, for microphones that capture quietly (a watch mic held at
 * arm's length is the motivating case).
 */
object PcmGain {

    /** The loudest 16-bit little-endian sample in [frame] (0..32768). */
    fun peak(frame: ByteArray): Int {
        var peak = 0
        var i = 0
        while (i + 1 < frame.size) {
            val sample = (frame[i].toInt() and 0xFF) or (frame[i + 1].toInt() shl 8)
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
            i += 2
        }
        return peak
    }

    /**
     * Scales every 16-bit little-endian sample in [frame] by [gain], in place, clipping at the
     * 16-bit limits. A trailing odd byte is left untouched.
     *
     * @return the loudest sample before the gain was applied (0..32768), so a caller can log how
     * loud the microphone really is.
     */
    fun apply(frame: ByteArray, gain: Float): Int {
        var peak = 0
        var i = 0
        while (i + 1 < frame.size) {
            val sample = (frame[i].toInt() and 0xFF) or (frame[i + 1].toInt() shl 8)
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            frame[i] = scaled.toByte()
            frame[i + 1] = (scaled shr 8).toByte()
            i += 2
        }
        return peak
    }
}
