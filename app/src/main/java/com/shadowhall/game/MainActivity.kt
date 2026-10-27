package com.shadowhall.game

import android.app.Activity
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.media.ToneGenerator

class MainActivity : Activity() {
    private lateinit var game: HallSurface
    private lateinit var hud: TextView
    private lateinit var status: TextView
    private lateinit var tone: ToneGenerator
    private val handler = Handler(Looper.getMainLooper())
    private val updater = object : Runnable { override fun run() { if (::game.isInitialized && !isFinishing) { status.text = game.renderer.statusText; handler.postDelayed(this, 200) } } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
        val root = FrameLayout(this)
        game = HallSurface(this)
        root.addView(game, FrameLayout.LayoutParams(-1, -1))
        hud = TextView(this).apply {
            text = "SHADOW HALL: الهروب من العجوز\nحرّك بإصبعك على اليسار، ولفّ الكاميرا بإصبع آخر على اليمين\nاضغط باستمرار على (جري) وأنت بتحرك لتجري وتغيّر اتجاهك في نفس الوقت"
            setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(0x990A0D18.toInt()); setPadding(18, 12, 18, 12)
        }
        root.addView(hud, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.START))
        status = TextView(this).apply { text = "اجمع 3 مفاتيح وافتح باب الهروب"; setTextColor(0xFFFFD56A.toInt()); textSize = 14f; setPadding(18, 8, 18, 8); setBackgroundColor(0x990A0D18.toInt()) }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 88 })
        val hide = TextView(this).apply {
            text = "اختباء"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC6D2635.toInt()); setPadding(28, 18, 28, 18)
            setOnClickListener {
                val wasHidden = game.renderer.hidden
                game.renderer.toggleHide()
                if (game.renderer.hidden != wasHidden) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                else tone.startTone(ToneGenerator.TONE_PROP_NACK, 100)
                text = if (game.renderer.hidden) "اخرج" else "اختباء"
            }
        }
        root.addView(hide, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0,0,28,22) })
        val light = TextView(this).apply {
            text = "كشاف"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC31546B.toInt()); setPadding(20, 18, 20, 18)
            setOnClickListener { game.renderer.flashlightOn = !game.renderer.flashlightOn; text = if (game.renderer.flashlightOn) "كشاف" else "الكشاف مغلق" }
        }
        root.addView(light, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(20,0,0,22) })
        val sprint = TextView(this).apply {
            text = "جري"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC7A421B.toInt()); setPadding(24, 18, 24, 18)
            setOnTouchListener { _, event ->
                val pressed = event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE
                game.renderer.setSprinting(pressed)
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) game.renderer.setSprinting(false)
                true
            }
        }
        root.addView(sprint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 22 })
        val restart = TextView(this).apply {
            text = "إعادة المحاولة"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC39404D.toInt()); setPadding(18, 12, 18, 12)
            setOnClickListener { game.renderer.restartGame(); hide.text = "اختباء" }
        }
        root.addView(restart, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = 145; rightMargin = 16 })
        val interact = TextView(this).apply {
            text = "تفاعل"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(0xCC53613A.toInt()); setPadding(24, 18, 24, 18)
            setOnClickListener { game.renderer.interact(); tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80) }
        }
        root.addView(interact, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0,0,28,100) })
        setContentView(root)
        handler.post(updater)
    }
    override fun onPause() { handler.removeCallbacks(updater); super.onPause(); game.onPause() }
    override fun onResume() { super.onResume(); game.onResume(); handler.post(updater) }
    override fun onDestroy() { handler.removeCallbacks(updater); if (::tone.isInitialized) tone.release(); super.onDestroy() }
}

class HallSurface(activity: Activity) : GLSurfaceView(activity) {
    val renderer = HallRenderer()
    private enum class TouchRole { MOVE, LOOK }
    private data class TouchTrack(
        val role: TouchRole,
        val downX: Float,
        val downY: Float,
        var lastX: Float,
        var lastY: Float
    )
    private val touches = mutableMapOf<Int, TouchTrack>()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
    }

    private fun beginPointer(e: MotionEvent, index: Int) {
        val id = e.getPointerId(index)
        val x = e.getX(index)
        val y = e.getY(index)
        val role = if (x < width * 0.48f) TouchRole.MOVE else TouchRole.LOOK
        touches[id] = TouchTrack(role, x, y, x, y)
    }

    private fun endPointer(id: Int) {
        touches.remove(id)
        updateMovement()
    }

    private fun updateMovement() {
        val move = touches.values.firstOrNull { it.role == TouchRole.MOVE }
        if (move == null) {
            renderer.moveX = 0f
            renderer.moveZ = 0f
        } else {
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            renderer.moveX = ((move.lastX - move.downX) / (w * 0.18f)).coerceIn(-1f, 1f)
            renderer.moveZ = ((move.lastY - move.downY) / (h * 0.18f)).coerceIn(-1f, 1f)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginPointer(e, e.actionIndex)
            MotionEvent.ACTION_POINTER_DOWN -> beginPointer(e, e.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val track = touches[id] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    if (track.role == TouchRole.LOOK) {
                        val dx = x - track.lastX
                        renderer.cameraYaw += (dx / width.toFloat().coerceAtLeast(1f)) * 220f
                    }
                    track.lastX = x
                    track.lastY = y
                }
                updateMovement()
            }
            MotionEvent.ACTION_POINTER_UP -> endPointer(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touches.clear()
                renderer.moveX = 0f
                renderer.moveZ = 0f
            }
        }
        return true
    }
}