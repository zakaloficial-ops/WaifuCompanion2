package com.hyper.companion

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import java.util.*

class OverlayService : Service(), TextToSpeech.OnInitListener {
    private lateinit var windowManager: WindowManager
    private lateinit var gifView: ImageView
    private var params: WindowManager.LayoutParams? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        setupOverlay()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
        }
    }

    private fun setupOverlay() {
        gifView = ImageView(this)
        gifView.visibility = View.INVISIBLE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }

        try {
            val file = java.io.File(filesDir, "waifu_gif.gif")
            if (file.exists()) {
                val source = android.graphics.ImageDecoder.createSource(file)
                val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
                gifView.setImageDrawable(drawable)
            }
        } catch (e: Exception) { e.printStackTrace() }

        gifView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(gifView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(gifView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "WaifuChannel")
            .setContentTitle("Компаньон активен")
            .setContentText("Слежу за тобой в фоне...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
        startCompanionLoop()
        return START_STICKY
    }

    private fun startCompanionLoop() {
        val delayMillis = (5 * 60 * 1000L) + random.nextInt(10 * 60 * 1000L)
        handler.postDelayed({
            showGifAndSpeak()
            startCompanionLoop() 
        }, delayMillis)
    }

    private fun showGifAndSpeak() {
        if (gifView.drawable != null) {
            gifView.visibility = View.VISIBLE
            (gifView.drawable as? AnimatedImageDrawable)?.start()
        }

        val prefs = getSharedPreferences("CompanionPrefs", Context.MODE_PRIVATE)
        val phrases = prefs.getStringSet("phrases", setOf("Я здесь.", "Как дела?"))?.toList()
        if (isTtsReady && !phrases.isNullOrEmpty()) {
            val phrase = phrases[random.nextInt(phrases.size)]
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "")
        }

        handler.postDelayed({
            gifView.visibility = View.INVISIBLE
            (gifView.drawable as? AnimatedImageDrawable)?.stop()
        }, 5000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("WaifuChannel", "Waifu Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tts?.shutdown()
        if (gifView.isAttachedToWindow) windowManager.removeView(gifView)
    }
}
