package pt.reborn.callai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import pt.reborn.callai.call.CallSessionService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = "REBORN CALL AI\n\nBUILD 1\nDigital GSM capture bridge skeleton\n\nNext: pair local ADB and attach privileged VOICE_CALL PCM daemon."
            textSize = 18f
            setPadding(32, 48, 32, 48)
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

        val startBridge = Button(this).apply {
            text = "Iniciar REBORN Bridge"
            setOnClickListener {
                if (hasCorePermissions()) {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, CallSessionService::class.java)
                    )
                    status.text = "REBORN CALL AI\n\nBridge ativo.\nÀ espera do daemon privilegiado de captura PCM."
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
