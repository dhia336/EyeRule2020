package com.eyerule.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.AnimationDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class BreakOverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var timer: CountDownTimer? = null
    private var toneGenerator: ToneGenerator? = null
    private var frameAnimation: AnimationDrawable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("eyerule_prefs", MODE_PRIVATE)
        val breakSeconds = prefs.getInt("break_seconds", 20)
        val unskippable = prefs.getBoolean("unskippable", false)
        if (prefs.getBoolean("play_sound", true)) {
            playBeep()
        }
        showOverlay(breakSeconds, unskippable)
        return START_NOT_STICKY
    }

    private fun playBeep() {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            toneGenerator = generator
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
        } catch (e: RuntimeException) {
            // Some devices refuse to allocate a ToneGenerator (e.g. audio in use
            // by something else) - silently skip the beep rather than crash.
        }
    }

    /**
     * Builds a looping frame animation from PNGs named "<prefix>_01.png",
     * "<prefix>_02.png", etc in res/drawable. Stops at the first missing
     * number, so any frame count works with no code changes - just add or
     * replace files with the same naming pattern. Returns null (and shows
     * nothing) if no frames are found at all.
     */
    private fun loadFrameAnimation(prefix: String, frameDurationMs: Int = 110): AnimationDrawable? {
        val anim = AnimationDrawable()
        var count = 0
        var i = 1
        while (true) {
            val name = "${prefix}_%02d".format(i)
            val resId = resources.getIdentifier(name, "drawable", packageName)
            if (resId == 0) break
            anim.addFrame(resources.getDrawable(resId, theme), frameDurationMs)
            count++
            i++
        }
        if (count == 0) return null
        anim.isOneShot = false
        return anim
    }

    private fun showOverlay(breakSeconds: Int, unskippable: Boolean) {
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }

        val accentColor = resources.getColor(R.color.accent, theme)

        val animation = loadFrameAnimation("lookaway")
        if (animation != null) {
            frameAnimation = animation
            val animationView = ImageView(this).apply {
                setImageDrawable(animation)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            root.addView(
                animationView,
                LinearLayout.LayoutParams(200.dp(), 200.dp())
            )
        }

        val message = TextView(this).apply {
            text = "Look at something 20 feet away\nfor $breakSeconds seconds"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 20)
        }

        val countdownText = TextView(this).apply {
            setTextColor(accentColor)
            textSize = 40f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        root.addView(message)
        root.addView(countdownText)

        if (!unskippable) {
            val skipButton = Button(this).apply {
                text = "Skip"
                setTextColor(Color.WHITE)
                textSize = 15f
                isAllCaps = false
                setBackgroundResource(R.drawable.bg_button_primary)
                setPadding(56, 20, 56, 20)
                stateListAnimator = null
                setOnClickListener { dismissOverlay() }
            }
            root.addView(
                skipButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 56 }
            )
        } else {
            // Block the back key so the break can't be dismissed early.
            // (The physical home/recents buttons can't be intercepted by a
            // regular overlay - that would need device-owner/accessibility
            // privileges - so this covers back-gesture/back-button only.)
            root.isFocusableInTouchMode = true
            root.setOnKeyListener { _, keyCode, _ ->
                keyCode == android.view.KeyEvent.KEYCODE_BACK
            }
            root.post { root.requestFocus() }
        }

        windowManager?.addView(root, params)
        overlayView = root
        frameAnimation?.start()

        // Auto-dismiss after the configured break duration, even if the user
        // never taps Skip.
        timer = object : CountDownTimer(breakSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                dismissOverlay()
            }
        }.start()
    }

    private fun dismissOverlay() {
        timer?.cancel()
        frameAnimation?.stop()
        frameAnimation = null
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null

        toneGenerator?.release()
        toneGenerator = null

        // Only now, once the pause has genuinely ended, arm the next break.
        // This keeps the real gap between breaks equal to the chosen interval,
        // rather than the interval being eaten into by however long the pause ran.
        val prefs = getSharedPreferences("eyerule_prefs", MODE_PRIVATE)
        if (AlarmScheduler.isRunning(prefs)) {
            AlarmScheduler.armNextAlarm(this, prefs)
        }

        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissOverlay()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
