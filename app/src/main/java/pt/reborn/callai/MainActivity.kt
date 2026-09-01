package pt.reborn.callai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.reborn.callai.adb.EmbeddedAdbManager
import pt.reborn.callai.call.CallSessionService
import pt.reborn.callai.telemetry.BridgeTelemetry

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var telemetry: TextView
    private val telemetryTicker = object : Runnable {
        override fun run() {
            if (::telemetry.isInitialized) telemetry.text = "DIAGNÓSTICO AO VIVO\n\n${BridgeTelemetry.render()}"
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val adbManager = EmbeddedAdbManager.get(applicationContext)

        val status = TextView(this).apply {
            text = "REBORN CALL AI\n\nEmbedded ADB + GSM PCM bridge\n\nPAIRING = porta temporária do código.\nADB NORMAL = porta fixa mostrada em 'Porta e endereço IP'."
            textSize = 18f
            setPadding(32, 48, 32, 32)
        }

        telemetry = TextView(this).apply {
            text = "DIAGNÓSTICO AO VIVO\n\nADB: WAITING\nDAEMON: WAITING\nVOICE_CALL PCM: WAITING"
            textSize = 16f
            setPadding(32, 24, 32, 24)
        }

        val permissions = Button(this).apply {
            text = "Autorizar telefone e áudio"
            setOnClickListener { requestRuntimePermissions() }
        }

        val wirelessDebug = Button(this).apply {
            text = "Abrir Wireless Debugging"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }

        val pairingPort = EditText(this).apply {
            hint = "Porta de pairing (temporária)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val pairingCode = EditText(this).apply {
            hint = "Código ADB de 6 dígitos"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val pairButton = Button(this).apply {
            text = "Emparelhar REBORN com o próprio S26"
            setOnClickListener {
                val port = pairingPort.text.toString().toIntOrNull()
                val code = pairingCode.text.toString().trim()
                if (port == null || code.length != 6) {
                    status.text = "Pairing: introduz a porta temporária e o código de 6 dígitos com a janela do Android aberta."
                    return@setOnClickListener
                }

                isEnabled = false
                status.text = "Pairing ADB local em 127.0.0.1:$port…"
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { adbManager.pairLocal(port, code) }
                    }
                    isEnabled = true
                    status.text = if (result.getOrDefault(false)) {
                        "ADB LOCAL ● EMPARELHADO\n\nAgora usa abaixo a porta ADB NORMAL mostrada em Wireless Debugging."
                    } else {
                        "ADB pairing falhou: ${result.exceptionOrNull()?.message ?: "código/porta recusados"}"
                    }
                }
            }
        }

        val connectHost = EditText(this).apply {
            hint = "IP ADB normal"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(adbManager.savedConnectHost())
        }

        val connectPort = EditText(this).apply {
            hint = "Porta ADB normal (ex: 40163)"
            inputType = InputType.TYPE_CLASS_NUMBER
            val saved = adbManager.savedConnectPort()
            if (saved > 0) setText(saved.toString())
        }

        val startBridge = Button(this).apply {
            text = "Ligar ADB normal + iniciar REBORN Bridge"
            setOnClickListener {
                val host = connectHost.text.toString().trim()
                val port = connectPort.text.toString().toIntOrNull()
                if (host.isBlank() || port == null) {
                    status.text = "Introduz o IP e a porta ADB NORMAL mostrados em Wireless Debugging."
                    return@setOnClickListener
                }

                if (hasCorePermissions()) {
                    adbManager.saveConnectEndpoint(host, port)
                    BridgeTelemetry.reset()
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, CallSessionService::class.java)
                    )
                    status.text = "REBORN Bridge ● A LIGAR A $host:$port\n\nVê o diagnóstico ao vivo abaixo."
                } else {
                    requestRuntimePermissions()
                }
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(status)
            addView(permissions)
            addView(wirelessDebug)
            addView(pairingPort)
            addView(pairingCode)
            addView(pairButton)
            addView(connectHost)
            addView(connectPort)
            addView(startBridge)
            addView(telemetry)
        }

        setContentView(layout)
        handler.post(telemetryTicker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(telemetryTicker)
        super.onDestroy()
    }

    private fun hasCorePermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }
}
