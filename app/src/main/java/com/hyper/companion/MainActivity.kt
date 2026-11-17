package com.hyper.companion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("CompanionPrefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etPhrase = findViewById<EditText>(R.id.etPhrase)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnLoadGif = findViewById<Button>(R.id.btnLoadGif)
        val btnStart = findViewById<Button>(R.id.btnStart)

        btnAdd.setOnClickListener {
            val text = etPhrase.text.toString().trim()
            if (text.isNotEmpty()) {
                val phrases = prefs.getStringSet("phrases", mutableSetOf("Привет, сенпай!", "Не забудь отдохнуть."))?.toMutableSet() ?: mutableSetOf()
                phrases.add(text)
                prefs.edit().putStringSet("phrases", phrases).apply()
                etPhrase.text.clear()
                Toast.makeText(this, "Фраза добавлена", Toast.LENGTH_SHORT).show()
            }
        }

        val gifPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    contentResolver.openInputStream(it)?.use { input ->
                        val file = java.io.File(filesDir, "waifu_gif.gif")
                        file.outputStream().use { output -> input.copyTo(output) }
                        Toast.makeText(this, "Гифка сохранена", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show() }
            }
        }

        btnLoadGif.setOnClickListener { gifPicker.launch("image/gif") }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Разреши плавающее окно в настройках!", Toast.LENGTH_LONG).show()
            } else {
                val serviceIntent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Компаньон запущен!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
