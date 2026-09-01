package pt.reborn.callai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = "REBORN CALL AI\n\nEmbedded ADB + GSM PCM bridge\n\n1. Autoriza permissões\n2. Mantém REBORN + Wireless debugging em ecrã dividido\n3. Abre Sincronize dispositivo com código\n4. Introduz apenas a porta e o código enquanto a janela continua aberta"
            textSize = 18f
            setPadding(32, 48, 32, 32)
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
            hint = "Porta de pairing"
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
                    status.text = "Pairing: introduz a porta e o código de 6 dígitos com a janela do Android ainda aberta."
                    return@setOnClickListener
                }

                isEnabled = false
                status.text = "Pairing ADB local em 127.0.0.1:$port…\nMantém a janela de sincronização aberta."
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            EmbeddedAdbManager.get(applicationContext).pairLocal(port, code)
                        }
                    }
                    isEnabled = true
                    status.text = if (result.getOrDefault(false)) {
                        "ADB LOCAL ● EMPARELHADO\n\nPróximo: iniciar REBORN Bridge e testar VOICE_CALL PCM."
                    } else {
                        "ADB pairing falhou: ${result.exceptionOrNull()?.message ?: "código/porta recusados"}\n\nMantém REBORN e a janela de sincronização lado a lado e gera um código novo."
                    }
                }
            }
        }

        val startBridge = Button(this).apply {
            text = "Iniciar REBORN Bridge"
            setOnClickListener {
                if (hasCorePermissions()) {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, CallSessionService::class.java)
                    )
                    status.text = "REBORN Bridge ● ATIVO\n\nADB transport preparado. À espera da ligação do daemon VOICE_CALL PCM."
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
            addView(startBridge)
        }

        setContentView(layout)
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
