package pt.reborn.callai.dialer

import android.telecom.Call
import android.telecom.InCallService
import pt.reborn.callai.telemetry.BridgeTelemetry

class RebornInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveCallStore.set(call)
        BridgeTelemetry.callState = "ACTIVE_CALL_ADDED"
        call.registerCallback(callback)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        ActiveCallStore.clear(call)
        BridgeTelemetry.callState = "IDLE"
        super.onCallRemoved(call)
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            BridgeTelemetry.callState = when (state) {
                Call.STATE_NEW -> "NEW"
                Call.STATE_DIALING -> "DIALING"
                Call.STATE_RINGING -> "RINGING"
                Call.STATE_ACTIVE -> "ACTIVE"
                Call.STATE_HOLDING -> "HOLDING"
                Call.STATE_DISCONNECTED -> "DISCONNECTED"
                Call.STATE_CONNECTING -> "CONNECTING"
                Call.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "STATE_$state"
            }
        }
    }
}

object ActiveCallStore {
    @Volatile private var active: Call? = null

    fun set(call: Call) { active = call }
    fun clear(call: Call) { if (active === call) active = null }
    fun current(): Call? = active

    fun answer(): Boolean {
        val call = active ?: return false
        call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        return true
    }

    fun hangup(): Boolean {
        val call = active ?: return false
        call.disconnect()
        return true
    }

    fun hold(): Boolean {
        val call = active ?: return false
        call.hold()
        return true
    }

    fun unhold(): Boolean {
        val call = active ?: return false
        call.unhold()
        return true
    }
}
