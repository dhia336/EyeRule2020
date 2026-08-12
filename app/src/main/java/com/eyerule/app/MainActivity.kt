package com.eyerule.app

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // Placeholder - swap for your real Gumroad link.
    private val supportUrl = "https://gumroad.com/"

    private lateinit var prefs: SharedPreferences

    private lateinit var progressRing: CircularProgressView
    private lateinit var countdownText: TextView
    private lateinit var statusBadgeLabel: TextView

    private lateinit var intervalCard: View
    private lateinit var breakCard: View
    private lateinit var intervalRing: CircularProgressView
    private lateinit var breakRing: CircularProgressView
    private lateinit var intervalValueText: TextView
    private lateinit var breakValueText: TextView

    private lateinit var soundSwitch: Switch
    private lateinit var unskippableSwitch: Switch
    private lateinit var resetOnUnlockSwitch: Switch
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

        progressRing = findViewById(R.id.progressRing)
        countdownText = findViewById(R.id.countdownText)
        statusBadgeLabel = findViewById(R.id.statusBadgeLabel)

        intervalCard = findViewById(R.id.intervalCard)
        breakCard = findViewById(R.id.breakCard)
        intervalRing = findViewById(R.id.intervalRing)
        breakRing = findViewById(R.id.breakRing)
        intervalValueText = findViewById(R.id.intervalValueText)
        breakValueText = findViewById(R.id.breakValueText)

        soundSwitch = findViewById(R.id.soundSwitch)
        unskippableSwitch = findViewById(R.id.unskippableSwitch)
        resetOnUnlockSwitch = findViewById(R.id.resetOnUnlockSwitch)
        toggleButton = findViewById(R.id.toggleButton)
        supportLink = findViewById(R.id.supportLink)

        setupRingColors()

        soundSwitch.isChecked = prefs.getBoolean("play_sound", true)
        unskippableSwitch.isChecked = prefs.getBoolean("unskippable", false)
        resetOnUnlockSwitch.isChecked = prefs.getBoolean("restart_on_unlock", false)

        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("play_sound", isChecked).apply()
        }
        unskippableSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("unskippable", isChecked).apply()
        }
        resetOnUnlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("restart_on_unlock", isChecked).apply()
        }

        intervalCard.setOnClickListener {
            if (!AlarmScheduler.isRunning(prefs)) {
                showNumberPickerDialog(
                    title = "Interval (minutes)",
                    min = 1, max = 120,
                    current = prefs.getInt("interval_minutes", 20)
                ) { picked ->
                    prefs.edit().putInt("interval_minutes", picked).apply()
                    refreshDialValues()
                }
            }
        }

        breakCard.setOnClickListener {
            if (!AlarmScheduler.isRunning(prefs)) {
                showNumberPickerDialog(
                    title = "Break length (seconds)",
                    min = 5, max = 120,
                    current = prefs.getInt("break_seconds", 20)
                ) { picked ->
                    prefs.edit().putInt("break_seconds", picked).apply()
                    refreshDialValues()
                }
            }
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
        refreshDialValues()
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

    private fun setupRingColors() {
        val track = resources.getColor(R.color.ring_track, theme)
        val accent = resources.getColor(R.color.accent, theme)
        val strokeMain = 16f.dp()
        val strokeSmall = 8f.dp()

        progressRing.trackColor = track
        progressRing.progressColor = accent
        progressRing.ringStrokeWidthPx = strokeMain

        listOf(intervalRing, breakRing).forEach {
            it.trackColor = track
            it.progressColor = accent
            it.ringStrokeWidthPx = strokeSmall
            it.progress = 1f
        }
    }

    private fun refreshDialValues() {
        val minutes = prefs.getInt("interval_minutes", 20)
        val breakSeconds = prefs.getInt("break_seconds", 20)
        intervalValueText.text = minutes.toString()
        breakValueText.text = breakSeconds.toString()
    }

    private fun showNumberPickerDialog(
        title: String,
        min: Int,
        max: Int,
        current: Int,
        onPicked: (Int) -> Unit
    ) {
        val picker = NumberPicker(this).apply {
            minValue = min
            maxValue = max
            value = current.coerceIn(min, max)
        }

        val container = android.widget.FrameLayout(this).apply {
            val pad = 24f.dp().toInt()
            setPadding(pad, pad, pad, pad)
            addView(
                picker,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = android.view.Gravity.CENTER }
            )
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Set") { _, _ -> onPicked(picker.value) }
            .setNegativeButton("Cancel", null)
            .show()
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

        val minutes = prefs.getInt("interval_minutes", 20)
        AlarmScheduler.schedule(this, prefs, minutes)
        updateUi()
    }

    private fun updateUi() {
        val running = AlarmScheduler.isRunning(prefs)

        statusBadgeLabel.text = if (running) "RUNNING" else "STOPPED"
        statusBadgeLabel.setTextColor(
            resources.getColor(if (running) R.color.cyan else R.color.text_muted, theme)
        )

        toggleButton.text = if (running) "Stop" else "Start"
        toggleButton.setBackgroundResource(if (running) R.drawable.bg_button_stop else R.drawable.bg_button_primary)

        val cardAlpha = if (running) 0.5f else 1f
        intervalCard.alpha = cardAlpha
        breakCard.alpha = cardAlpha
        intervalCard.isEnabled = !running
        breakCard.isEnabled = !running

        updatePulse(running)
        updateCountdown()
    }

    /** A slow, gentle breathing pulse on the status label while running - calm, not urgent. */
    private fun updatePulse(running: Boolean) {
        if (running) {
            if (pulseAnimator == null) {
                val animator = ObjectAnimator.ofFloat(statusBadgeLabel, View.ALPHA, 1f, 0.4f).apply {
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
            statusBadgeLabel.alpha = 1f
        }
    }

    private fun updateCountdown() {
        val remaining = AlarmScheduler.millisRemaining(prefs)
        if (remaining == null) {
            countdownText.text = "--:--"
            progressRing.progress = 0f
            return
        }

        val totalSeconds = remaining / 1000
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        countdownText.text = "%02d:%02d".format(m, s)

        val totalMillis = prefs.getInt("interval_minutes", 20) * 60_000L
        val elapsedFraction = if (totalMillis > 0) {
            1f - (remaining.toFloat() / totalMillis.toFloat())
        } else 0f
        progressRing.progress = elapsedFraction.coerceIn(0f, 1f)
    }

    /** Soft, staggered fade + rise on first load - deliberately understated. */
    private fun playEntranceAnimation() {
        val header = findViewById<View>(R.id.headerRow)
        val statusCard = findViewById<View>(R.id.statusCard)
        val settingsCard = findViewById<View>(R.id.settingsCard)

        val views = listOf(header, statusCard, intervalCard, breakCard, settingsCard, toggleButton, supportLink)
        views.forEach {
            it.alpha = 0f
            it.translationY = 28f
        }

        views.forEachIndexed { index, view ->
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 60L)
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

    private fun Float.dp(): Float = this * resources.displayMetrics.density
}
