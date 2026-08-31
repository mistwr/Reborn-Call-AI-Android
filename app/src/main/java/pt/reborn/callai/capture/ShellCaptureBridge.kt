package pt.reborn.callai.capture

import android.content.Context
import android.util.Log
import pt.reborn.callai.audio.PcmFrame

/**
 * Client-side facade for privileged call capture.
 *
 * A stock app process cannot open VOICE_CALL directly. The production implementation
 * will bind to a local shell-side daemon started through embedded/local ADB and receive
 * PCM through Binder/shared memory. This class intentionally does NOT fall back to the
 * microphone, because REBORN needs digital call audio for transcription.
 */
class ShellCaptureBridge(
    private val context: Context,
) : CallAudioCapture {

    override var isRunning: Boolean = false
        private set

    private var sink: ((PcmFrame) -> Unit)? = null

    override fun start(onFrame: (PcmFrame) -> Unit) {
        check(!isRunning) { "Capture already running" }
        sink = onFrame
        isRunning = true
        Log.i(TAG, "Shell capture bridge armed; waiting for privileged daemon")
        // BUILD 2: connect Binder/shared-memory transport here.
    }

    override fun stop() {
        isRunning = false
        sink = null
        Log.i(TAG, "Shell capture bridge stopped")
    }

    /** Called by the future Binder/shared-memory receiver. */
    internal fun onPrivilegedPcm(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
    ) {
        if (!isRunning) return
        sink?.invoke(PcmFrame(samples, sampleRate, channels))
    }

    companion object {
        private const val TAG = "RebornShellCapture"
    }
}
