package pt.reborn.callai.capture

import pt.reborn.callai.audio.PcmFrame

interface CallAudioCapture {
    val isRunning: Boolean
    fun start(onFrame: (PcmFrame) -> Unit)
    fun stop()
}
