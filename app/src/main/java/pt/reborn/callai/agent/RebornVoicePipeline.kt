package pt.reborn.callai.agent

import android.util.Log
import pt.reborn.callai.audio.PcmFrame

class RebornVoicePipeline {

    fun acceptRemoteAudio(frame: PcmFrame) {
        // BUILD 2/3:
        // 1) split remote channel when stereo capture is available
        // 2) stream PCM to STT
        // 3) send transcript to REBORN LLM
        // 4) stream TTS to the uplink/output adapter
        Log.d(TAG, "PCM ${frame.samples.size} samples @ ${frame.sampleRate}Hz ch=${frame.channels}")
    }

    companion object {
        private const val TAG = "RebornVoicePipeline"
    }
}
