package pt.reborn.callai.capture

import android.content.Context
import android.util.Log
import io.github.muntashirakon.adb.AdbStream
import pt.reborn.callai.adb.EmbeddedAdbManager
import pt.reborn.callai.audio.PcmFrame
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Digital GSM capture bridge.
 *
 * The normal app opens a loopback TCP listener. A shell-uid app_process daemon is then launched via
 * the already-paired embedded ADB connection. The daemon owns AudioRecord(VOICE_CALL) and streams
 * PCM16LE back to this process over 127.0.0.1.
 *
 * There is deliberately NO microphone fallback: if privileged VOICE_CALL cannot be opened, capture
 * fails visibly instead of silently transcribing speakerphone audio.
 */
class ShellCaptureBridge(
    private val context: Context,
) : CallAudioCapture {

    override var isRunning: Boolean = false
        private set

    @Volatile var lastError: String? = null
        private set

    @Volatile var sampleRate: Int = 0
        private set

    @Volatile var channels: Int = 0
        private set

    private var sink: ((PcmFrame) -> Unit)? = null
    private var serverSocket: ServerSocket? = null
    private var adbStream: AdbStream? = null
    private var worker: Thread? = null
    private val stopRequested = AtomicBoolean(false)

    override fun start(onFrame: (PcmFrame) -> Unit) {
        check(!isRunning) { "Capture already running" }
        sink = onFrame
        lastError = null
        sampleRate = 0
        channels = 0
        stopRequested.set(false)
        isRunning = true

        worker = Thread({ runBridge() }, "reborn-call-capture").also { it.start() }
    }

    private fun runBridge() {
        try {
            val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).also {
                it.soTimeout = 20_000
                serverSocket = it
            }
            val port = listener.localPort

            val adb = EmbeddedAdbManager.get(context)
            check(adb.ensureConnected()) {
                "ADB local não ligado. Faz primeiro o pairing em Wireless Debugging."
            }

            val apk = context.applicationInfo.sourceDir
            val fqcn = "pt.reborn.callai.daemon.RebornPcmDaemon"
            val command = "CLASSPATH='$apk' exec app_process / $fqcn $port"
            Log.i(TAG, "Launching shell PCM daemon on loopback port $port")
            adbStream = adb.openShell(command)

            listener.accept().use { socket ->
                socket.tcpNoDelay = true
                val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
                val header = readAsciiLine(input)
                val parts = header.trim().split(' ')
                check(parts.size == 3 && parts[0] == "REBORN_PCM_V1") {
                    "Resposta inválida do daemon: $header"
                }

                sampleRate = parts[1].toInt()
                channels = parts[2].toInt()
                check(sampleRate > 0 && channels in 1..2) {
                    "Formato PCM inválido: $sampleRate Hz / $channels canais"
                }
                Log.i(TAG, "VOICE_CALL PCM active: ${sampleRate}Hz, ${channels}ch")

                val frameSamples = 960 * channels
                val byteBuffer = ByteArray(frameSamples * 2)
                while (!stopRequested.get()) {
                    val got = readFullyOrEof(input, byteBuffer)
                    if (got <= 0) break
                    val even = got - (got % 2)
                    if (even == 0) continue
                    val shorts = ShortArray(even / 2)
                    ByteBuffer.wrap(byteBuffer, 0, even)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shorts)
                    sink?.invoke(PcmFrame(shorts, sampleRate, channels))
                }
            }
        } catch (t: Throwable) {
            if (!stopRequested.get()) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Digital VOICE_CALL capture failed", t)
            }
        } finally {
            cleanup()
            isRunning = false
        }
    }

    override fun stop() {
        stopRequested.set(true)
        cleanup()
        worker?.interrupt()
        worker = null
        sink = null
        isRunning = false
        Log.i(TAG, "Shell capture bridge stopped")
    }

    private fun cleanup() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { adbStream?.close() }
        adbStream = null
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val out = StringBuilder()
        while (out.length < 128) {
            val b = input.read()
            if (b == -1) break
            if (b == '\n'.code) return out.toString()
            if (b != '\r'.code) out.append(b.toChar())
        }
        return out.toString()
    }

    private fun readFullyOrEof(input: BufferedInputStream, target: ByteArray): Int {
        var offset = 0
        while (offset < target.size && !stopRequested.get()) {
            val n = input.read(target, offset, target.size - offset)
            if (n < 0) break
            if (n == 0) continue
            offset += n
        }
        return offset
    }

    companion object {
        private const val TAG = "RebornShellCapture"
    }
}
