package com.ticktock.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ticktock.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var analyzer: AudioBeatAnalyzer? = null
    private var running = false
    private val beatLogLock = Any()
    private val loggedBeats = mutableListOf<String>()
    private var acceptingBeatLog = false

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
        synchronized(beatLogLock) {
            loggedBeats.clear()
            acceptingBeatLog = true
        }
        binding.startButton.text = "Stop"
        binding.statusText.text = "Searching beat..."
        binding.tickText.text = "Mean period [ms]\n--"
        running = true

        analyzer = AudioBeatAnalyzer(initialGuessPeriodMs = guessPeriodMs) { metrics ->
            synchronized(beatLogLock) {
                if (acceptingBeatLog) {
                    loggedBeats += String.format(Locale.US, "%.3f", metrics.beatTimeSeconds)
                }
            }
            runOnUiThread { renderMetrics(metrics) }
        }.also { it.start() }
    }

    private fun stopAnalyzer() {
        if (!running && analyzer == null) return
        analyzer?.stop()
        analyzer = null
        val beats = synchronized(beatLogLock) {
            acceptingBeatLog = false
            loggedBeats.toList()
        }
        running = false
        binding.startButton.text = "Start"
        binding.beatDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.ticktock_red)
        binding.statusText.text = "Searching beat..."
        val meanPeriodStats = calculateMeanPeriodStats(
            beatDeltas(beats),
            trimEnd = true
        )
        binding.tickText.text = formatMeanPeriod(meanPeriodStats)
        saveBeatLog(beats)
    }

    private fun saveBeatLog(beats: List<String>) {
        val fileName = "ticktockBeats_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.txt"
        thread(name = "BeatLogWriter", isDaemon = true) {
            var uri = null as android.net.Uri?
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
                uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    ?: error("Could not create the beat log")
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer ?: error("Could not open the beat log")
                    writer.write(beats.joinToString(separator = "\n"))
                    if (beats.isNotEmpty()) writer.newLine()
                }
                runOnUiThread { showSaveToast("Beat log saved to Documents") }
            } catch (error: Exception) {
                uri?.let { contentResolver.delete(it, null, null) }
                runOnUiThread { showSaveToast("Could not save beat log") }
            }
        }
    }

    private fun beatDeltas(beats: List<String>): List<Double> {
        val beatTimes = beats.mapNotNull { it.toDoubleOrNull() }
        return beatTimes.zipWithNext { previous, current -> (current - previous) * 1_000.0 }
            .filter { it in 120.0..4_000.0 }
    }

    private fun formatMeanPeriod(stats: MeanPeriodStats?): String {
        if (stats == null) return "Mean period [ms]\n--"
        return "Mean period [ms]\n${format(stats.meanPeriodMs)} ± ${format(stats.standardDeviationMs)}"
    }

    private fun showSaveToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_LONG)
        toast.show()
        binding.root.postDelayed({ toast.cancel() }, 3_000)
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
        if (metrics.meanPeriodMs > 0.0) {
            binding.tickText.text = formatMeanPeriod(
                MeanPeriodStats(metrics.meanPeriodMs, metrics.meanPeriodStdDevMs)
            )
        }
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
        scaleText(binding.tickText, 25f, scale)
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
        updateTopMargin(binding.tickText, (8f * scale).toInt())
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
