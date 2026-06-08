package com.ticktock.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class BeatMetrics(
    val periodMs: Double,
    val asymmetryMs: Double,
    val asymmetryPercent: Double,
    val beatLabel: Char,
    val acquiring: Boolean
)

class AudioBeatAnalyzer(
    private val initialGuessPeriodMs: Long,
    private val onMetrics: (BeatMetrics) -> Unit
) {
    private val sampleRate = 44_100
    private val highPass1 = Biquad.highPass(sampleRate.toDouble(), 600.0, 0.707)
    private val highPass2 = Biquad.highPass(sampleRate.toDouble(), 600.0, 0.707)
    private val lowPass1 = Biquad.lowPass(sampleRate.toDouble(), 5000.0, 0.707)
    private val lowPass2 = Biquad.lowPass(sampleRate.toDouble(), 5000.0, 0.707)

    @Volatile
    private var running = false

    private var worker: Thread? = null
    private var prevEnvelope = 0.0
    private var envelope = 0.0
    private var noiseFloor = 0.0

    private var beatCount = 0
    private var lastBeatTimeNs = 0L
    private var lastBeatLabel = 'R'
    private val maxRefinementBeats = 40

    private val lDurations = ArrayDeque<Double>()
    private val rDurations = ArrayDeque<Double>()

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "AudioBeatAnalyzer", isDaemon = true) {
            runLoop()
        }
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
    }

    private fun runLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onMetrics(
                BeatMetrics(
                    periodMs = 0.0,
                    asymmetryMs = 0.0,
                    asymmetryPercent = 0.0,
                    beatLabel = 'X',
                    acquiring = true
                )
            )
            return
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, 4096)
        )

        val buffer = ShortArray(1024)
        val halfBeatGuessMs = initialGuessPeriodMs / 2.0
        val refractoryMs = (halfBeatGuessMs * 0.4).coerceIn(60.0, 1_600.0).toLong()

        record.startRecording()
        try {
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                processAudioBlock(buffer, read, refractoryMs)
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
            record.release()
        }
    }

    private fun processAudioBlock(data: ShortArray, count: Int, refractoryMs: Long) {
        val nowNs = System.nanoTime()
        for (i in 0 until count) {
            val sample = data[i] / 32768.0
            val bp = lowPass2.process(lowPass1.process(highPass2.process(highPass1.process(sample))))
            val rectified = abs(bp)

            // Fast attack, slower decay envelope.
            val attack = 0.45
            val decay = 0.015
            envelope = if (rectified > envelope) {
                attack * rectified + (1.0 - attack) * envelope
            } else {
                decay * rectified + (1.0 - decay) * envelope
            }

            noiseFloor = 0.001 * envelope + 0.999 * noiseFloor
            val threshold = noiseFloor * 3.5 + 0.003
            val risingCross = prevEnvelope < threshold && envelope >= threshold
            prevEnvelope = envelope

            if (!risingCross) continue

            val beatTimeNs = nowNs + (i * 1_000_000_000L / sampleRate)
            if (lastBeatTimeNs != 0L && (beatTimeNs - lastBeatTimeNs) / 1_000_000 < refractoryMs) {
                continue
            }

            beatCount += 1
            val beatLabel = if (beatCount % 2 == 1) 'L' else 'R'

            if (lastBeatTimeNs != 0L) {
                val deltaMs = (beatTimeNs - lastBeatTimeNs) / 1_000_000.0
                // Tracking and asymmetry computation continue after fit refinement ends.
                if (lastBeatLabel == 'L' && beatLabel == 'R') {
                    addDuration(lDurations, deltaMs)
                } else if (lastBeatLabel == 'R' && beatLabel == 'L') {
                    addDuration(rDurations, deltaMs)
                }
            }

            lastBeatTimeNs = beatTimeNs
            lastBeatLabel = beatLabel
            emitMetrics(beatLabel)
        }
    }

    private fun addDuration(queue: ArrayDeque<Double>, value: Double) {
        if (value < 120 || value > 4_000) return
        queue.addLast(value)
        while (queue.size > 14) queue.removeFirst()
    }

    private fun emitMetrics(label: Char) {
        val l = if (lDurations.isNotEmpty()) lDurations.average() else 0.0
        val r = if (rDurations.isNotEmpty()) rDurations.average() else 0.0
        val acquiring = lDurations.size < 3 || rDurations.size < 3 || beatCount < 10

        if (l > 0.0 && r > 0.0) {
            val period = l + r
            val asymMs = l - r
            val asymPct = 100.0 * asymMs / period
            onMetrics(BeatMetrics(period, asymMs, asymPct, label, acquiring))
        } else {
            onMetrics(BeatMetrics(0.0, 0.0, 0.0, label, true))
        }
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        companion object {
            fun highPass(fs: Double, f0: Double, q: Double): Biquad {
                val w0 = 2.0 * PI * f0 / fs
                val alpha = sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val b0 = (1.0 + cosW0) / 2.0
                val b1 = -(1.0 + cosW0)
                val b2 = (1.0 + cosW0) / 2.0
                val a0 = 1.0 + alpha
                val a1 = -2.0 * cosW0
                val a2 = 1.0 - alpha
                return normalize(b0, b1, b2, a0, a1, a2)
            }

            fun lowPass(fs: Double, f0: Double, q: Double): Biquad {
                val w0 = 2.0 * PI * f0 / fs
                val alpha = sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val b0 = (1.0 - cosW0) / 2.0
                val b1 = 1.0 - cosW0
                val b2 = (1.0 - cosW0) / 2.0
                val a0 = 1.0 + alpha
                val a1 = -2.0 * cosW0
                val a2 = 1.0 - alpha
                return normalize(b0, b1, b2, a0, a1, a2)
            }

            private fun normalize(
                b0: Double,
                b1: Double,
                b2: Double,
                a0: Double,
                a1: Double,
                a2: Double
            ): Biquad {
                return Biquad(
                    b0 / a0,
                    b1 / a0,
                    b2 / a0,
                    a1 / a0,
                    a2 / a0
                )
            }
        }
    }
}
