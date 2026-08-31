package pt.reborn.callai.audio

data class PcmFrame(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int,
    val timestampNanos: Long = System.nanoTime(),
)
