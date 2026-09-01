package pt.reborn.callai.agent

import android.util.Log
import pt.reborn.callai.audio.PcmFrame
import pt.reborn.callai.audio.StereoChannelSplitter
import pt.reborn.callai.telemetry.BridgeTelemetry

class RebornVoicePipeline {

    private val splitter = StereoChannelSplitter()

    @Volatile var lastLeftMeanAbs: Double = 0.0
        private set

    @Volatile var lastRightMeanAbs: Double = 0.0
        private set

    fun acceptRemoteAudio(frame: PcmFrame) {
        if (frame.channels == 2) {
            val split = splitter.split(frame) ?: return
            lastLeftMeanAbs = split.leftMeanAbs
            lastRightMeanAbs = split.rightMeanAbs
            BridgeTelemetry.leftLevel = split.leftMeanAbs
            BridgeTelemetry.rightLevel = split.rightMeanAbs
            Log.d(
                TAG,
                "VOICE_CALL stereo ${frame.sampleRate}Hz L=${"%.1f".format(split.leftMeanAbs)} R=${"%.1f".format(split.rightMeanAbs)}"
            )
            return
        }

        val mean = if (frame.samples.isEmpty()) 0.0 else frame.samples.sumOf { kotlin.math.abs(it.toInt()).toLong() }.toDouble() / frame.samples.size
        BridgeTelemetry.leftLevel = mean
        Log.d(TAG, "VOICE_CALL mono ${frame.samples.size} samples @ ${frame.sampleRate}Hz mean=${"%.1f".format(mean)}")
    }

    companion object {
        private const val TAG = "RebornVoicePipeline"
    }
}
