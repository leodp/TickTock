package com.ticktock.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ticktock.app.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var analyzer: AudioBeatAnalyzer? = null
    private var running = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAnalyzer()
        } else {
            binding.statusText.text = "Microphone permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyDynamicLayoutScaling()

        binding.startButton.setOnClickListener {
            if (running) {
                stopAnalyzer()
            } else {
                requestAndStart()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopAnalyzer()
    }

    private fun requestAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startAnalyzer()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAnalyzer() {
        val guessPeriodMs = binding.guessInput.text.toString().toLongOrNull()?.coerceIn(400, 8000) ?: 1200L
        binding.guessInput.setText(guessPeriodMs.toString())
        binding.startButton.text = "Stop"
        binding.statusText.text = "Searching beat..."
        running = true

        analyzer = AudioBeatAnalyzer(initialGuessPeriodMs = guessPeriodMs) { metrics ->
            runOnUiThread { renderMetrics(metrics) }
        }.also { it.start() }
    }

    private fun stopAnalyzer() {
        analyzer?.stop()
        analyzer = null
        running = false
        binding.startButton.text = "Start"
        binding.beatDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.ticktock_red)
        binding.statusText.text = "Searching beat..."
    }

    private fun renderMetrics(metrics: BeatMetrics) {
        if (metrics.acquiring) {
            binding.statusText.text = "Searching beat..."
        } else {
            binding.statusText.text = "Tracking beat"
        }

        val dotColor = when (metrics.beatLabel) {
            'L' -> R.color.ticktock_blue
            'R' -> R.color.ticktock_green
            else -> R.color.ticktock_red
        }

        binding.beatDot.backgroundTintList = ContextCompat.getColorStateList(this, dotColor)
        binding.beatDot.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(90)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.beatDot.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            .start()

        if (metrics.periodMs <= 0.0) return

        binding.gaugeView.setAsymmetryPercent(metrics.asymmetryPercent)
        binding.periodText.text = "Period [ms]\n${format(metrics.periodMs)}"
        binding.asymmetryMsText.text = "Asymmetry [ms]\n${format(metrics.asymmetryMs)}"
        binding.asymmetryPercentText.text = "Asymmetry [%]\n${format(metrics.asymmetryPercent)}"
    }

    private fun format(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun applyDynamicLayoutScaling() {
        val dm = resources.displayMetrics
        val width = dm.widthPixels.toFloat()
        val height = dm.heightPixels.toFloat()
        val scale = minOf(width / 1080f, height / 1920f).coerceIn(0.78f, 1.15f)

        val rootPadding = (12f * scale).toInt()
        binding.root.setPadding(rootPadding, rootPadding, rootPadding, rootPadding)

        scaleText(binding.titleText, 37.5f, scale)
        scaleText(binding.guessLabel, 25f, scale)
        scaleText(binding.guessInput, 25f, scale)
        scaleText(binding.startButton, 25f, scale)
        scaleText(binding.asymmetryPercentText, 25f, scale)
        scaleText(binding.asymmetryMsText, 25f, scale)
        scaleText(binding.periodText, 25f, scale)
        scaleText(binding.statusText, 25f, scale)
        scaleText(binding.footerText, 18f, scale)

        updateHeight(binding.gaugeView, (300f * scale).toInt())
        updateSize(binding.beatDot, (84f * scale).toInt(), (84f * scale).toInt())

        val horizontalMargin = (resources.displayMetrics.widthPixels * 0.10f).toInt()
        updateHorizontalMargins(binding.asymmetryPercentText, horizontalMargin)
        updateHorizontalMargins(binding.asymmetryMsText, horizontalMargin)
        updateHorizontalMargins(binding.periodText, horizontalMargin)

        updateTopMargin(binding.titleText, (resources.displayMetrics.heightPixels * 0.05f).toInt())
        updateTopMargin(binding.guessLabel, (10f * scale).toInt())
        updateTopMargin(binding.startButton, (10f * scale).toInt())
        updateTopMargin(binding.gaugeView, (10f * scale).toInt())
        updateTopMargin(binding.asymmetryPercentText, (6f * scale).toInt())
        updateTopMargin(binding.asymmetryMsText, (6f * scale).toInt())
        updateTopMargin(binding.periodText, (6f * scale).toInt())
        updateTopMargin(binding.beatRow, (8f * scale).toInt())
        updateTopMargin(binding.footerText, (8f * scale).toInt())
    }

    private fun scaleText(view: TextView, sp: Float, scale: Float) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale)
    }

    private fun updateHeight(view: View, hPx: Int) {
        val lp = view.layoutParams
        lp.height = hPx
        view.layoutParams = lp
    }

    private fun updateSize(view: View, wPx: Int, hPx: Int) {
        val lp = view.layoutParams
        lp.width = wPx
        lp.height = hPx
        view.layoutParams = lp
    }

    private fun updateTopMargin(view: View, marginPx: Int) {
        val lp = view.layoutParams
        if (lp is ViewGroup.MarginLayoutParams) {
            lp.topMargin = marginPx
            view.layoutParams = lp
        }
    }

    private fun updateHorizontalMargins(view: View, marginPx: Int) {
        val lp = view.layoutParams
        if (lp is ViewGroup.MarginLayoutParams) {
            lp.marginStart = marginPx
            lp.marginEnd = marginPx
            view.layoutParams = lp
        }
    }
}
