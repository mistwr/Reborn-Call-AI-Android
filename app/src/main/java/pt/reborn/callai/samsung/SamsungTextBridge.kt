package pt.reborn.callai.samsung

import android.content.Context
import android.content.Intent
import android.provider.Settings

object SamsungTextBridge {
    @Volatile var lastQueuedText: String? = null
        private set

    @Volatile var lastResult: String = "IDLE"
        internal set

    fun queue(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            lastQueuedText = clean
            lastResult = "QUEUED"
            RebornSamsungAccessibilityService.consumePending()
        }
    }

    internal fun takePending(): String? {
        val value = lastQueuedText
        lastQueuedText = null
        return value
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
