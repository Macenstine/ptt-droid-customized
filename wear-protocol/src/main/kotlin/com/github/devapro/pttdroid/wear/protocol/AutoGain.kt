package com.github.devapro.pttdroid.wear.protocol

/**
 * A simple automatic gain for a quiet microphone: it raises each frame towards [TARGET_PEAK]
 * and never boosts what is only background noise.
 *
 * - **Fast attack:** a frame that would clip at the current gain pulls the gain down at once.
 * - **Slow release:** the gain creeps back up over several frames, so it does not pump on every
 *   pause between words.
 * - **Noise floor:** frames quieter than [NOISE_FLOOR] leave the gain alone instead of being
 *   amplified into hiss.
 *
 * One instance per transmission; not thread-safe.
 */
class AutoGain(initialGain: Float = INITIAL_GAIN) {

    /** The gain the next loud-enough frame will start from. */
    var gain: Float = initialGain
        private set

    /** Amplifies [frame] in place and returns its peak before the gain. */
    fun apply(frame: ByteArray): Int {
        val peak = PcmGain.peak(frame)
        if (peak >= NOISE_FLOOR) {
            val wanted = (TARGET_PEAK / peak).coerceIn(MIN_GAIN, MAX_GAIN)
            gain = if (wanted < gain) wanted else gain + (wanted - gain) * RELEASE
        }
        PcmGain.apply(frame, gain)
        return peak
    }

    companion object {
        /**
         * About -4 dBFS. The gain for a frame never exceeds what brings *that* frame to this
         * level, so the output cannot clip however loud the speaker gets.
         */
        const val TARGET_PEAK = 20_000f

        /** Below this raw peak (about -40 dBFS) a frame is treated as silence. */
        const val NOISE_FLOOR = 300

        const val MIN_GAIN = 1f
        const val MAX_GAIN = 20f

        /** Where a press starts, before it has heard anything. */
        const val INITIAL_GAIN = 6f

        /** Fraction of the way back up to the wanted gain per frame. */
        private const val RELEASE = 0.1f
    }
}
