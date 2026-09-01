package pt.reborn.callai.audio

import kotlin.math.abs

/**
 * Splits interleaved PCM16 stereo into two mono streams and tracks simple activity energy.
 *
 * We intentionally do not guess which side is the customer yet. Samsung/vendor routing can map
 * uplink/downlink differently per device/firmware. The first S26 test will expose L/R activity so
 * REBORN can persist the correct remote-channel mapping once verified.
 */
class StereoChannelSplitter {

    data class Split(
        val left: PcmFrame,
        val right: PcmFrame,
        val leftMeanAbs: Double,
        val rightMeanAbs: Double,
    )

    fun split(frame: PcmFrame): Split? {
        if (frame.channels != 2 || frame.samples.size < 2) return null

        val frames = frame.samples.size / 2
        val left = ShortArray(frames)
        val right = ShortArray(frames)
        var leftEnergy = 0L
        var rightEnergy = 0L

        var src = 0
        for (i in 0 until frames) {
            val l = frame.samples[src++]
            val r = frame.samples[src++]
            left[i] = l
            right[i] = r
            leftEnergy += abs(l.toInt()).toLong()
            rightEnergy += abs(r.toInt()).toLong()
        }

        return Split(
            left = PcmFrame(left, frame.sampleRate, 1),
            right = PcmFrame(right, frame.sampleRate, 1),
            leftMeanAbs = leftEnergy.toDouble() / frames,
            rightMeanAbs = rightEnergy.toDouble() / frames,
        )
    }
}
