package pt.reborn.callai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.reborn.callai.adb.EmbeddedAdbManager
import pt.reborn.callai.agent.RebornCampaigns
import pt.reborn.callai.backend.RebornBackend
import pt.reborn.callai.call.CallSessionService
import pt.reborn.callai.dialer.ActiveCallStore
import pt.reborn.callai.dialer.DialerRoleHelper
import pt.reborn.callai.telemetry.BridgeTelemetry

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var telemetry: TextView
    private lateinit var dialerRoleState: TextView
    private lateinit var status: TextView

    private val telemetryTicker = object : Runnable {
        override fun run() {
            if (::telemetry.isInitialized) telemetry.text = BridgeTelemetry.render()
            if (::dialerRoleState.isInitialized) {
                dialerRoleState.text = if (DialerRoleHelper.isDefaultDialer(this@MainActivity)) {
                    "● REBORN é a app de chamadas deste telefone"
                } else {
                    "○ Samsung Phone ainda é a app principal"
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adbManager = EmbeddedAdbManager.get(applicationContext)
        val backend = RebornBackend(applicationContext)
        val campaign = RebornCampaigns.myPouparQualification

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 13, 22))
            setPadding(28, 30, 28, 40)
        }

        status = title("REBORN AI CALL", 28f).apply {
            text = "REBORN AI CALL\nA tua plataforma nativa de chamadas + IA"
            setTextColor(Color.rgb(103, 255, 224))
        }
        root.addView(status)
        root.addView(body("SD Dialer · REBORN AI · MY POUPar+ · Indigo · Supabase", 14f))

        root.addView(section("TELEFONE"))
        dialerRoleState = body("A verificar app de chamadas…", 15f)
        root.addView(dialerRoleState)

        root.addView(action("USAR REBORN COMO APP DE CHAMADAS") {
            DialerRoleHelper.requestDefaultDialer(this)
        })

        val number = EditText(this).apply {
            hint = "Número de telefone"
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            intent?.data?.schemeSpecificPart?.let { setText(it) }
        }
        root.addView(number)

        root.addView(action("📞 LIGAR COM REBORN AI") {
            val phone = number.text.toString().trim()
            if (phone.isBlank()) {
                status.text = "Introduz um número para ligar."
            } else {
                placePhoneCall(phone)
            }
        })

        val callControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        callControls.addView(smallAction("ATENDER") { ActiveCallStore.answer() })
        callControls.addView(smallAction("DESLIGAR") { ActiveCallStore.hangup() })
        callControls.addView(smallAction("PAUSA") { ActiveCallStore.hold() })
        root.addView(callControls)

        root.addView(section("AGENTS REBORN"))
        root.addView(agentCard("VOICE CALLER", "Fala, ouve, interrompe e conduz a conversa."))
        root.addView(agentCard("SALES AGENT", "Qualifica, trata objeções e identifica intenção de compra."))
        root.addView(agentCard("SUPPORT AGENT", "Esclarece dúvidas e encaminha para humano quando necessário."))
        root.addView(agentCard("ANALYTICS AGENT", "Resume chamadas, motivos, conversão e qualidade."))
        root.addView(agentCard("AUTOMATION AGENT", "Atualiza CRM, estados, tarefas e follow-ups."))
        root.addView(agentCard("SCHEDULER AGENT", "Marca retorno e agenda contacto com o gestor."))

        root.addView(section("CAMPANHA ATIVA"))
        root.addView(title(campaign.name, 19f))
        root.addView(body(campaign.opening, 16f))
        root.addView(body("Resposta positiva: a IA marca a chamada como LEAD QUENTE e envia o contacto para SD Dialer/Supabase. O backend pode replicar o evento para Indigo e MY POUPar+.", 14f))

        root.addView(action("🔥 TESTAR LEAD QUENTE") {
            val phone = number.text.toString().trim()
            if (phone.isBlank()) {
                status.text = "Introduz primeiro um número."
                return@action
            }
            lifecycleScope.launch {
                status.text = "A enviar lead quente…"
                val result = withContext(Dispatchers.IO) {
                    backend.submitHotLead(phone, campaign.id, "manual_test", "sim")
                }
                status.text = if (result.isSuccess) {
                    "🔥 Lead quente enviada para o ecossistema REBORN."
                } else {
                    "Falha ao enviar lead: ${result.exceptionOrNull()?.message}"
                }
            }
        })

        root.addView(section("CÉREBRO / BACKEND"))
        val backendUrl = EditText(this).apply {
            hint = "https://teu-sd-dialer..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(backend.getBaseUrl())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(backendUrl)
        root.addView(action("GUARDAR ENDPOINT REBORN") {
            backend.saveBaseUrl(backendUrl.text.toString())
            status.text = "Endpoint REBORN guardado."
        })

        root.addView(section("MOTOR GSM / PCM"))
        root.addView(action("AUTORIZAR TELEFONE E ÁUDIO") { requestRuntimePermissions() })
        root.addView(action("ABRIR WIRELESS DEBUGGING") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        })

        val pairingPort = editNumber("Porta de pairing temporária")
        val pairingCode = editNumber("Código ADB de 6 dígitos")
        root.addView(pairingPort)
        root.addView(pairingCode)
        root.addView(action("EMPARELHAR MOTOR REBORN") {
            val port = pairingPort.text.toString().toIntOrNull()
            val code = pairingCode.text.toString().trim()
            if (port == null || code.length != 6) {
                status.text = "Introduz porta de pairing e código de 6 dígitos."
                return@action
            }
            lifecycleScope.launch {
                status.text = "A emparelhar ADB local…"
                val result = withContext(Dispatchers.IO) {
                    runCatching { adbManager.pairLocal(port, code) }
                }
                status.text = if (result.getOrDefault(false)) "ADB LOCAL ● EMPARELHADO" else "Pairing falhou: ${result.exceptionOrNull()?.message}"
            }
        })

        val connectHost = EditText(this).apply {
            hint = "IP ADB normal"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(adbManager.savedConnectHost())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val connectPort = editNumber("Porta ADB normal").apply {
            val saved = adbManager.savedConnectPort()
            if (saved > 0) setText(saved.toString())
        }
        root.addView(connectHost)
        root.addView(connectPort)
        root.addView(action("INICIAR REBORN BRIDGE") {
            val host = connectHost.text.toString().trim()
            val port = connectPort.text.toString().toIntOrNull()
            if (host.isBlank() || port == null) {
                status.text = "Indica IP e porta ADB normal."
                return@action
            }
            if (!hasCorePermissions()) {
                requestRuntimePermissions()
                return@action
            }
            adbManager.saveConnectEndpoint(host, port)
            BridgeTelemetry.reset()
            ContextCompat.startForegroundService(this, Intent(this, CallSessionService::class.java))
            status.text = "REBORN Bridge a ligar a $host:$port"
        })

        root.addView(section("DIAGNÓSTICO AO VIVO"))
        telemetry = body("CALL: IDLE\nADB: WAITING\nDAEMON: WAITING\nVOICE_CALL PCM: WAITING", 15f)
        root.addView(telemetry)

        root.addView(section("FLUXO AUTOMÁTICO"))
        root.addView(body("1. REBORN liga pelo SIM\n2. Voice Caller apresenta a MY POUPar+\n3. PCM digital → STT → REBORN Agent\n4. Cliente diz “sim” ou marca 1\n5. Lead passa a HOT no SD Dialer/Supabase\n6. Indigo e MY POUPar+ recebem o evento\n7. Gestor humano recebe a lead quente\n8. Futuro: TTS da IA injetado diretamente no uplink GSM", 15f))

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        handler.post(telemetryTicker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(telemetryTicker)
        super.onDestroy()
    }

    private fun placePhoneCall(phone: String) {
        if (!hasCorePermissions()) {
            requestRuntimePermissions()
            return
        }
        val uri = Uri.parse("tel:$phone")
        if (DialerRoleHelper.isDefaultDialer(this)) {
            val telecom = getSystemService(TelecomManager::class.java)
            telecom.placeCall(uri, Bundle())
        } else {
            startActivity(Intent(Intent.ACTION_CALL, uri))
        }
    }

    private fun hasCorePermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    private fun section(text: String) = title(text, 13f).apply {
        setTextColor(Color.rgb(103, 255, 224))
        setPadding(0, 34, 0, 12)
    }

    private fun title(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, 8, 0, 8)
    }

    private fun body(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.rgb(205, 220, 225))
        setPadding(0, 8, 0, 12)
    }

    private fun action(text: String, listener: View.OnClickListener) = Button(this).apply {
        this.text = text
        setOnClickListener(listener)
        isAllCaps = false
        setTextColor(Color.rgb(4, 22, 27))
        setBackgroundColor(Color.rgb(103, 255, 224))
    }

    private fun smallAction(text: String, block: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { block() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun editNumber(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
    }

    private fun agentCard(name: String, description: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(22, 18, 22, 18)
        addView(title(name, 17f).apply { setTextColor(Color.rgb(103, 255, 224)) })
        addView(body(description, 14f))
    }
}
