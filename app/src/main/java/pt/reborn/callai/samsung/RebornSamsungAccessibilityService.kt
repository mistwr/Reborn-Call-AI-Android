package pt.reborn.callai.samsung

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RebornSamsungAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        SamsungTextBridge.lastResult = "ACCESSIBILITY_READY"
        consumePending()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
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
        val input = findEditable(root) ?: return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        val send = findSendNode(root)
        return send?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            findEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun findSendNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = listOfNotNull(node.text, node.contentDescription)
            .joinToString(" ")
            .lowercase()
        val looksLikeSend = label.contains("enviar") || label.contains("send") || label.contains("falar") || label.contains("speak")
        if (node.isClickable && node.isVisibleToUser && looksLikeSend) return node
        for (i in 0 until node.childCount) {
            findSendNode(node.getChild(i))?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile private var instance: RebornSamsungAccessibilityService? = null

        fun consumePending() {
            val service = instance ?: return
            val text = SamsungTextBridge.takePending() ?: return
            val ok = runCatching { service.tryInjectAndSend(text) }.getOrDefault(false)
            SamsungTextBridge.lastResult = if (ok) "SENT_TO_SAMSUNG" else "SAMSUNG_FIELD_NOT_FOUND"
            if (!ok) SamsungTextBridge.restorePending(text)
        }
    }
}
