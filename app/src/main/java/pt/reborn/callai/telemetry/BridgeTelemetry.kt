package pt.reborn.callai.telemetry

object BridgeTelemetry {
    @Volatile var adbConnected: Boolean = false
    @Volatile var daemonStarted: Boolean = false
    @Volatile var pcmActive: Boolean = false
    @Volatile var sampleRate: Int = 0
    @Volatile var channels: Int = 0
    @Volatile var leftLevel: Double = 0.0
    @Volatile var rightLevel: Double = 0.0
    @Volatile var frames: Long = 0
    @Volatile var callState: String = "IDLE"
    @Volatile var lastError: String? = null

    fun reset() {
        adbConnected = false
        daemonStarted = false
        pcmActive = false
        sampleRate = 0
        channels = 0
        leftLevel = 0.0
        rightLevel = 0.0
        frames = 0
        lastError = null
    }

    fun render(): String = buildString {
        append("CALL: ").append(callState).append('\n')
        append("ADB: ").append(if (adbConnected) "CONNECTED" else "WAITING").append('\n')
        append("DAEMON: ").append(if (daemonStarted) "STARTED" else "WAITING").append('\n')
        append("VOICE_CALL PCM: ").append(if (pcmActive) "ACTIVE" else "WAITING").append('\n')
        if (pcmActive) {
            append("FORMAT: ").append(sampleRate).append(" Hz / ").append(channels).append(" ch\n")
            append("FRAMES: ").append(frames).append('\n')
            if (channels == 2) {
                append("L: ").append("%.1f".format(leftLevel))
                    .append("   R: ").append("%.1f".format(rightLevel)).append('\n')
            }
        }
        lastError?.let { append("ERROR: ").append(it) }
    }
}
