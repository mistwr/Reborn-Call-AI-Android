package pt.reborn.callai.agent

import android.content.Context
import pt.reborn.callai.audio.PcmFrame
import pt.reborn.callai.backend.RebornBackend
import pt.reborn.callai.samsung.SamsungTextBridge
import pt.reborn.callai.telemetry.BridgeTelemetry
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

class RebornAutoConversationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val backend = RebornBackend(appContext)
    private val worker = Executors.newSingleThreadExecutor()

    private val speechBuffer = ArrayList<Short>(240_000)
    private var speaking = false
    private var silenceSamples = 0
    private var sampleRate = 48_000
    private var busy = false
    private var lastTranscript = ""
    private val history = ArrayList<Pair<String, String>>()

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        BridgeTelemetry.autoConversation = enabled
        if (!enabled) resetTurn()
    }

    fun getRemoteChannel(): String = prefs.getString(KEY_CHANNEL, "left") ?: "left"

    fun setRemoteChannel(channel: String) {
        val safe = if (channel == "right") "right" else "left"
        prefs.edit().putString(KEY_CHANNEL, safe).apply()
        BridgeTelemetry.remoteChannel = safe.uppercase()
    }

    fun accept(frame: PcmFrame) {
        BridgeTelemetry.autoConversation = isEnabled()
        BridgeTelemetry.remoteChannel = getRemoteChannel().uppercase()
        if (!isEnabled() || busy || frame.samples.isEmpty()) return

        val mono = selectChannel(frame)
        if (mono.isEmpty()) return
        sampleRate = frame.sampleRate

        val mean = mono.sumOf { abs(it.toInt()).toLong() }.toDouble() / mono.size
        val speechNow = mean >= SPEECH_THRESHOLD

        if (!speaking) {
            if (!speechNow) return
            speaking = true
            silenceSamples = 0
            speechBuffer.clear()
            BridgeTelemetry.conversationState = "LISTENING"
        }

        mono.forEach { speechBuffer.add(it) }

        if (speechNow) silenceSamples = 0 else silenceSamples += mono.size

        val bufferedSeconds = speechBuffer.size.toDouble() / sampleRate.coerceAtLeast(1)
        val silenceSeconds = silenceSamples.toDouble() / sampleRate.coerceAtLeast(1)

        if ((silenceSeconds >= SILENCE_TO_FINISH_SECONDS && bufferedSeconds >= MIN_TURN_SECONDS) || bufferedSeconds >= MAX_TURN_SECONDS) {
            val turn = ShortArray(speechBuffer.size) { speechBuffer[it] }
            resetTurn()
            processTurn(turn, sampleRate)
        }
    }

    private fun processTurn(samples: ShortArray, rate: Int) {
        if (busy) return
        busy = true
        BridgeTelemetry.conversationState = "TRANSCRIBING"
        val wav = wav(samples, rate)

        worker.execute {
            try {
                val transcript = backend.transcribeWav(wav).getOrThrow().trim()
                if (transcript.isBlank() || transcript.equals(lastTranscript, ignoreCase = true)) {
                    BridgeTelemetry.conversationState = "WAITING"
                    return@execute
                }
                lastTranscript = transcript
                BridgeTelemetry.lastTranscript = transcript.take(180)
                BridgeTelemetry.conversationState = "THINKING"

                val reply = backend.askAgent(
                    customerText = transcript,
                    campaign = "MY POUPar+",
                    previousMessages = history.toList(),
                ).getOrThrow().trim()

                if (reply.isBlank()) {
                    BridgeTelemetry.conversationState = "WAITING"
                    return@execute
                }

                history += "user" to transcript
                history += "assistant" to reply
                while (history.size > 12) history.removeAt(0)

                BridgeTelemetry.lastAgentReply = reply.take(180)
                BridgeTelemetry.conversationState = "SENDING_TO_SAMSUNG"
                SamsungTextBridge.queue(reply)
                BridgeTelemetry.conversationState = "WAITING"
            } catch (t: Throwable) {
                BridgeTelemetry.conversationState = "ERROR"
                BridgeTelemetry.lastError = "AUTO: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun selectChannel(frame: PcmFrame): ShortArray {
        if (frame.channels <= 1) return frame.samples
        val offset = if (getRemoteChannel() == "right") 1 else 0
        val frames = frame.samples.size / frame.channels
        return ShortArray(frames) { i -> frame.samples[i * frame.channels + offset] }
    }

    private fun resetTurn() {
        speaking = false
        silenceSamples = 0
        speechBuffer.clear()
    }

    private fun wav(samples: ShortArray, rate: Int): ByteArray {
        val pcmBytes = samples.size * 2
        val out = ByteArrayOutputStream(44 + pcmBytes)
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le16(v: Int) {
            out.write(v and 0xff)
            out.write((v ushr 8) and 0xff)
        }
        fun le32(v: Int) {
            out.write(v and 0xff)
            out.write((v ushr 8) and 0xff)
            out.write((v ushr 16) and 0xff)
            out.write((v ushr 24) and 0xff)
        }

        ascii("RIFF")
        le32(36 + pcmBytes)
        ascii("WAVE")
        ascii("fmt ")
        le32(16)
        le16(1)
        le16(1)
        le32(rate)
        le32(rate * 2)
        le16(2)
        le16(16)
        ascii("data")
        le32(pcmBytes)
        samples.forEach {
            val v = it.toInt()
            out.write(v and 0xff)
            out.write((v ushr 8) and 0xff)
        }
        return out.toByteArray()
    }

    fun shutdown() {
        worker.shutdownNow()
    }

    companion object {
        private const val PREFS = "reborn_auto_conversation"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CHANNEL = "remote_channel"
        private const val SPEECH_THRESHOLD = 220.0
        private const val SILENCE_TO_FINISH_SECONDS = 0.65
        private const val MIN_TURN_SECONDS = 0.45
        private const val MAX_TURN_SECONDS = 5.0
    }
}
