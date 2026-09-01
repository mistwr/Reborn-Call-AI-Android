package pt.reborn.callai.backend

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RebornBackend(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""

    fun saveBaseUrl(value: String) {
        prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()
    }

    fun submitHotLead(
        phone: String,
        campaignId: String,
        trigger: String,
        transcript: String? = null,
    ): Result<Unit> = runCatching {
        val base = getBaseUrl()
        require(base.startsWith("https://")) { "Configura primeiro o endpoint HTTPS do REBORN/SD Dialer" }

        val url = URL("$base/api/reborn/hot-lead")
        val payload = JSONObject()
            .put("phone", phone)
            .put("campaign_id", campaignId)
            .put("trigger", trigger)
            .put("transcript", transcript ?: JSONObject.NULL)
            .put("source", "reborn_ai_call_android")
            .put("status", "HOT")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = connection.responseCode
        require(code in 200..299) { "Backend respondeu HTTP $code" }
        connection.disconnect()
    }

    companion object {
        private const val PREFS = "reborn_backend"
        private const val KEY_BASE_URL = "base_url"
    }
}
