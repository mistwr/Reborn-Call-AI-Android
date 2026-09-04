package pt.reborn.callai.samsung

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import pt.reborn.callai.telemetry.BridgeTelemetry

class RebornSamsungAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        SamsungTextBridge.lastResult = "ACCESSIBILITY_READY"
        inspectActiveWindow(null)
        consumePending()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        inspectActiveWindow(event)
        if (SamsungTextBridge.lastQueuedText != null) consumePending()
    }

    override fun onInterrupt() {
        SamsungTextBridge.lastResult = "ACCESSIBILITY_INTERRUPTED"
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun tryInjectAndSend(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val input = findBestEditable(root) ?: return false

        BridgeTelemetry.samsungLastEditableId = input.viewIdResourceName.orEmpty()

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        val send = findBestSendNode(root, input)
        if (send != null) {
            BridgeTelemetry.samsungLastClickableId = send.viewIdResourceName.orEmpty()
            return send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // Some Samsung layouts expose an IME action rather than a labelled send button.
        return input.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
    }

    private fun inspectActiveWindow(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        BridgeTelemetry.samsungPackage = (event?.packageName ?: root.packageName)?.toString().orEmpty()
        BridgeTelemetry.samsungWindowClass = (event?.className ?: root.className)?.toString().orEmpty()

        var editableCount = 0
        var clickableCount = 0
        val preview = ArrayList<String>()

        fun walk(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            if (node.isEditable && node.isVisibleToUser) editableCount++
            if (node.isClickable && node.isVisibleToUser) clickableCount++

            if (preview.size < 24 && node.isVisibleToUser) {
                val bits = listOfNotNull(
                    node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { "id=$it" },
                    node.className?.toString()?.takeIf { it.isNotBlank() }?.let { "class=${it.substringAfterLast('.')}" },
                    node.text?.toString()?.takeIf { it.isNotBlank() }?.let { "text=${it.take(32)}" },
                    node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { "desc=${it.take(32)}" },
                    if (node.isEditable) "editable" else null,
                    if (node.isClickable) "clickable" else null,
                )
                if (bits.isNotEmpty()) preview += bits.joinToString("|")
            }

            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }

        walk(root)
        BridgeTelemetry.samsungEditableCount = editableCount
        BridgeTelemetry.samsungClickableCount = clickableCount
        BridgeTelemetry.samsungTreePreview = preview.joinToString("  »  ").take(1400)
    }

    private fun findBestEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collect(root) { node ->
            if (node.isVisibleToUser && (node.isEditable || supportsSetText(node))) candidates += node
        }
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { node ->
            val haystack = listOfNotNull(
                node.viewIdResourceName,
                node.text?.toString(),
                node.hintText?.toString(),
                node.contentDescription?.toString(),
            ).joinToString(" ").lowercase()
            var score = 0
            if (node.isEditable) score += 20
            if (supportsSetText(node)) score += 20
            if (haystack.contains("text") || haystack.contains("message") || haystack.contains("mensagem")) score += 20
            if (haystack.contains("call") || haystack.contains("chamada") || haystack.contains("bixby")) score += 10
            if (node.isFocused) score += 10
            score
        }
    }

    private fun findBestSendNode(root: AccessibilityNodeInfo, input: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collect(root) { node -> if (node.isClickable && node.isVisibleToUser) candidates += node }
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { node ->
            val haystack = listOfNotNull(
                node.viewIdResourceName,
                node.text?.toString(),
                node.contentDescription?.toString(),
            ).joinToString(" ").lowercase()
            var score = 0
            if (haystack.contains("send") || haystack.contains("enviar")) score += 100
            if (haystack.contains("speak") || haystack.contains("falar")) score += 90
            if (haystack.contains("voice") || haystack.contains("voz")) score += 50
            if (haystack.contains("text") || haystack.contains("message") || haystack.contains("mensagem")) score += 20
            val inputBounds = android.graphics.Rect().also { input.getBoundsInScreen(it) }
            val nodeBounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            val dy = kotlin.math.abs(nodeBounds.centerY() - inputBounds.centerY())
            if (dy < 180) score += 20
            score
        }?.takeIf { node ->
            val haystack = listOfNotNull(
                node.viewIdResourceName,
                node.text?.toString(),
                node.contentDescription?.toString(),
            ).joinToString(" ").lowercase()
            haystack.contains("send") || haystack.contains("enviar") ||
                haystack.contains("speak") || haystack.contains("falar") ||
                haystack.contains("voice") || haystack.contains("voz")
        }
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean =
        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }

    private fun collect(node: AccessibilityNodeInfo?, block: (AccessibilityNodeInfo) -> Unit) {
        if (node == null) return
        block(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), block)
    }

    companion object {
        @Volatile private var instance: RebornSamsungAccessibilityService? = null

        fun consumePending() {
            val service = instance ?: return
            val text = SamsungTextBridge.takePending() ?: return
            val ok = runCatching {
                service.inspectActiveWindow(null)
                service.tryInjectAndSend(text)
            }.getOrDefault(false)
            SamsungTextBridge.lastResult = if (ok) "SENT_TO_SAMSUNG" else "SAMSUNG_FIELD_NOT_FOUND"
            if (!ok) SamsungTextBridge.restorePending(text)
        }
    }
}
