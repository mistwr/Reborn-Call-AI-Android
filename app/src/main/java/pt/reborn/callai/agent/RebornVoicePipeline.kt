package pt.reborn.callai.agent

import android.util.Log
import pt.reborn.callai.audio.PcmFrame
import pt.reborn.callai.audio.StereoChannelSplitter

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

            // Do not guess the remote side yet. First S26 call will tell us which channel is
            // downlink/customer and we will persist that mapping for the device.
            Log.d(
                TAG,
                "VOICE_CALL stereo ${frame.sampleRate}Hz L=${"%.1f".format(split.leftMeanAbs)} R=${"%.1f".format(split.rightMeanAbs)}"
            )
            return
        }

        Log.d(TAG, "VOICE_CALL mono ${frame.samples.size} samples @ ${frame.sampleRate}Hz")
        // Next stage: stream the verified remote/customer mono PCM into STT.
    }

    companion object {
        private const val TAG = "RebornVoicePipeline"
    }
}
