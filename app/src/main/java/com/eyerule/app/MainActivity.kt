package com.eyerule.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // Placeholder - swap for your real Gumroad link.
    private val supportUrl = "https://gumroad.com/"

    private lateinit var prefs: SharedPreferences
    private lateinit var intervalInput: EditText
    private lateinit var breakSecondsInput: EditText
    private lateinit var soundCheckbox: CheckBox
    private lateinit var unskippableCheckbox: CheckBox
    private lateinit var resetOnUnlockCheckbox: CheckBox
    private lateinit var statusDot: View
    private lateinit var statusBadgeLabel: TextView
    private lateinit var countdownText: TextView
    private lateinit var toggleButton: Button
    private lateinit var supportLink: TextView

    private var pulseAnimator: ObjectAnimator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("eyerule_prefs", MODE_PRIVATE)
        intervalInput = findViewById(R.id.intervalInput)
        breakSecondsInput = findViewById(R.id.breakSecondsInput)
        soundCheckbox = findViewById(R.id.soundCheckbox)
        unskippableCheckbox = findViewById(R.id.unskippableCheckbox)
        resetOnUnlockCheckbox = findViewById(R.id.resetOnUnlockCheckbox)
        statusDot = findViewById(R.id.statusDot)
        statusBadgeLabel = findViewById(R.id.statusBadgeLabel)
        countdownText = findViewById(R.id.countdownText)
        toggleButton = findViewById(R.id.toggleButton)
        supportLink = findViewById(R.id.supportLink)

        intervalInput.setText(prefs.getInt("interval_minutes", 20).toString())
        breakSecondsInput.setText(prefs.getInt("break_seconds", 20).toString())
        soundCheckbox.isChecked = prefs.getBoolean("play_sound", true)
        unskippableCheckbox.isChecked = prefs.getBoolean("unskippable", false)
        resetOnUnlockCheckbox.isChecked = prefs.getBoolean("restart_on_unlock", false)

        soundCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("play_sound", isChecked).apply()
        }
        unskippableCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("unskippable", isChecked).apply()
        }
        resetOnUnlockCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("restart_on_unlock", isChecked).apply()
        }

        toggleButton.setOnClickListener {
            if (AlarmScheduler.isRunning(prefs)) {
                AlarmScheduler.cancel(this, prefs)
                updateUi()
            } else {
                startRule()
            }
        }

        supportLink.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl)))
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't open link", Toast.LENGTH_SHORT).show()
            }
        }

        requestNotificationPermissionIfNeeded()
        updateUi()
        if (savedInstanceState == null) {
            playEntranceAnimation()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
    }

    private fun startRule() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Grant 'Display over other apps' permission, then hit Start again", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        if (!hasExactAlarmPermission()) {
            Toast.makeText(this, "Grant 'Alarms & reminders' permission, then hit Start again", Toast.LENGTH_LONG).show()
            requestExactAlarmPermission()
            return
        }

        val minutes = intervalInput.text.toString().toIntOrNull()
        val breakSeconds = breakSecondsInput.text.toString().toIntOrNull()

        if (minutes == null || minutes <= 0) {
            Toast.makeText(this, "Enter a valid interval in minutes", Toast.LENGTH_SHORT).show()
            return
        }
        if (breakSeconds == null || breakSeconds <= 0) {
            Toast.makeText(this, "Enter a valid break length in seconds", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putInt("break_seconds", breakSeconds).apply()
        AlarmScheduler.schedule(this, prefs, minutes)
        updateUi()
    }

    private fun updateUi() {
        val running = AlarmScheduler.isRunning(prefs)

        statusBadgeLabel.text = if (running) "Running" else "Stopped"
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
            resources.getColor(if (running) R.color.success_dot else R.color.text_muted, theme)
        )

        toggleButton.text = if (running) "Stop" else "Start"
        toggleButton.setBackgroundResource(if (running) R.drawable.bg_button_stop else R.drawable.bg_button_primary)

        intervalInput.isEnabled = !running
        breakSecondsInput.isEnabled = !running

        updatePulse(running)
        updateCountdown()
    }

    /** A slow, gentle breathing pulse on the status dot while the rule is running - calm, not urgent. */
    private fun updatePulse(running: Boolean) {
        if (running) {
            if (pulseAnimator == null) {
                val animator = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0.35f).apply {
                    duration = 1400
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                }
                pulseAnimator = animator
                animator.start()
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            statusDot.alpha = 1f
        }
    }

    private fun updateCountdown() {
        val remaining = AlarmScheduler.millisRemaining(prefs)
        countdownText.text = if (remaining == null) {
            "--:--"
        } else {
            val totalSeconds = remaining / 1000
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            "%02d:%02d".format(m, s)
        }
    }

    /** Soft, staggered fade + rise on first load - deliberately understated. */
    private fun playEntranceAnimation() {
        val header = findViewById<View>(R.id.headerRow)
        val statusCard = findViewById<View>(R.id.statusCard)
        val settingsCard = findViewById<View>(R.id.settingsCard)

        val views = listOf(header, statusCard, settingsCard, toggleButton, supportLink)
        views.forEach {
            it.alpha = 0f
            it.translationY = 28f
        }

        views.forEachIndexed { index, view ->
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            return am.canScheduleExactAlarms()
        }
        return true
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            startActivity(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}
