package pt.reborn.callai.backend

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RebornBackend(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""

    fun saveBaseUrl(value: String) {
        prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()
    }

    fun getAgentKey(): String = prefs.getString(KEY_AGENT_KEY, "") ?: ""

    fun saveAgentKey(value: String) {
        prefs.edit().putString(KEY_AGENT_KEY, value.trim()).apply()
    }

    fun askAgent(
        customerText: String,
        campaign: String = "MY POUPar+",
        operator: String? = null,
        previousMessages: List<Pair<String, String>> = emptyList(),
    ): Result<String> = runCatching {
        val base = getBaseUrl()
        require(base.startsWith("https://")) { "Configura primeiro o endpoint HTTPS do REBORN/SD Dialer" }
        require(customerText.isNotBlank()) { "Texto do cliente vazio" }

        val messages = JSONArray()
        previousMessages.takeLast(12).forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        messages.put(JSONObject().put("role", "user").put("content", customerText.trim()))

        val payload = JSONObject()
            .put("messages", messages)
            .put("campaign", campaign)
        if (!operator.isNullOrBlank()) payload.put("operator", operator)

        val connection = openJsonPost("$base/api/reborn/agent")
        val key = getAgentKey()
        if (key.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $key")
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        require(code in 200..299) { "Agent respondeu HTTP $code: $body" }

        val reply = JSONObject(body).optString("reply").trim()
        require(reply.isNotBlank()) { "Agent não devolveu resposta" }
        reply
    }

    fun submitHotLead(
        phone: String,
        campaignId: String,
        trigger: String,
        transcript: String? = null,
    ): Result<Unit> = runCatching {
        val base = getBaseUrl()
        require(base.startsWith("https://")) { "Configura primeiro o endpoint HTTPS do REBORN/SD Dialer" }

        val payload = JSONObject()
            .put("phone", phone)
            .put("campaign_id", campaignId)
            .put("trigger", trigger)
            .put("transcript", transcript ?: JSONObject.NULL)
            .put("source", "reborn_ai_call_android")
            .put("status", "HOT")

        val connection = openJsonPost("$base/api/reborn/hot-lead")
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = connection.responseCode
        require(code in 200..299) { "Backend respondeu HTTP $code" }
        connection.disconnect()
    }

    private fun openJsonPost(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

    companion object {
        private const val PREFS = "reborn_backend"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_AGENT_KEY = "agent_key"
    }
}
