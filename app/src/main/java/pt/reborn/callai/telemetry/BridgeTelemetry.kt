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
    @Volatile var autoConversation: Boolean = false
    @Volatile var remoteChannel: String = "LEFT"
    @Volatile var conversationState: String = "OFF"
    @Volatile var lastTranscript: String = ""
    @Volatile var lastAgentReply: String = ""
    @Volatile var lastError: String? = null

    @Volatile var samsungPackage: String = ""
    @Volatile var samsungWindowClass: String = ""
    @Volatile var samsungEditableCount: Int = 0
    @Volatile var samsungClickableCount: Int = 0
    @Volatile var samsungLastEditableId: String = ""
    @Volatile var samsungLastClickableId: String = ""
    @Volatile var samsungTreePreview: String = ""

    fun reset() {
        adbConnected = false
        daemonStarted = false
        pcmActive = false
        sampleRate = 0
        channels = 0
        leftLevel = 0.0
        rightLevel = 0.0
        frames = 0
        lastTranscript = ""
        lastAgentReply = ""
        lastError = null
        samsungPackage = ""
        samsungWindowClass = ""
        samsungEditableCount = 0
        samsungClickableCount = 0
        samsungLastEditableId = ""
        samsungLastClickableId = ""
        samsungTreePreview = ""
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
        append("AUTO AI: ").append(if (autoConversation) "ON" else "OFF")
            .append(" · REMOTE=").append(remoteChannel).append('\n')
        append("AI STATE: ").append(conversationState).append('\n')
        if (lastTranscript.isNotBlank()) append("CLIENTE: ").append(lastTranscript).append('\n')
        if (lastAgentReply.isNotBlank()) append("REBORN: ").append(lastAgentReply).append('\n')

        append("SAMSUNG PACKAGE: ").append(samsungPackage.ifBlank { "WAITING" }).append('\n')
        append("SAMSUNG WINDOW: ").append(samsungWindowClass.ifBlank { "WAITING" }).append('\n')
        append("EDITABLES: ").append(samsungEditableCount)
            .append(" · CLICKABLES: ").append(samsungClickableCount).append('\n')
        if (samsungLastEditableId.isNotBlank()) append("EDITABLE ID: ").append(samsungLastEditableId).append('\n')
        if (samsungLastClickableId.isNotBlank()) append("CLICK ID: ").append(samsungLastClickableId).append('\n')
        if (samsungTreePreview.isNotBlank()) append("TREE: ").append(samsungTreePreview).append('\n')

        lastError?.let { append("ERROR: ").append(it) }
    }
}
