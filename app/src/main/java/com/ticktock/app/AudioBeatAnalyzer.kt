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
    val acquiring: Boolean,
    val beatTimeSeconds: Double = 0.0,
    val meanPeriodMs: Double = 0.0,
    val meanPeriodStdDevMs: Double = 0.0
)

data class MeanPeriodStats(
    val meanPeriodMs: Double,
    val standardDeviationMs: Double
)

fun calculateMeanPeriodStats(deltas: List<Double>, trimEnd: Boolean): MeanPeriodStats? {
    if (deltas.isEmpty()) return null

    val trimCount = (deltas.size * 0.10).toInt()
    val trimmed = deltas.drop(trimCount).let { values ->
        if (trimEnd) values.dropLast(trimCount) else values
    }
    if (trimmed.isEmpty()) return null

    val sorted = trimmed.sorted()
    val middle = sorted.size / 2
    val median = if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    }
    val initialMean = trimmed.average()
    val initialStandardDeviation = kotlin.math.sqrt(
        trimmed.map { delta -> (delta - initialMean) * (delta - initialMean) }.average()
    )
    val filtered = if (initialStandardDeviation == 0.0) {
        trimmed
    } else {
        trimmed.filter {
            it in median - 5.0 * initialStandardDeviation..median + 5.0 * initialStandardDeviation
        }
    }
    if (filtered.isEmpty()) return null

    val meanBeatMs = filtered.average()
    val standardDeviationBeatMs = kotlin.math.sqrt(
        filtered.map { delta -> (delta - meanBeatMs) * (delta - meanBeatMs) }.average()
    )
    return MeanPeriodStats(
        meanPeriodMs = meanBeatMs * 2.0,
        standardDeviationMs = standardDeviationBeatMs * 2.0
    )
}

class AudioBeatAnalyzer(
    private val initialGuessPeriodMs: Long,
    private val onMetrics: (BeatMetrics) -> Unit
) {
    private var sampleRate = 192_000
    private lateinit var highPass1: Biquad
    private lateinit var highPass2: Biquad
    private lateinit var lowPass1: Biquad
    private lateinit var lowPass2: Biquad

    @Volatile
    private var running = false

    private var worker: Thread? = null
    private var prevEnvelope = 0.0
    private var envelope = 0.0
    private var rawEnvelope = 0.0
    private var noiseFloor = 0.0
    private var inputGain = 1.0

    private var beatCount = 0
    private var lastBeatTimeNs = 0L
    private var firstBeatTimeNs = 0L
    private var lastBeatLabel = 'R'
    private var peakActive = false
    private var peakEnvelope = 0.0
    private var peakTimeNs = 0L
    private var startupPeakDiscarded = false
    private val minRefinementBeats = 10
    private val maxRefinementBeats = 40
    private var lockedRefractoryMs: Long? = null

    private val lDurations = ArrayDeque<Double>()
    private val rDurations = ArrayDeque<Double>()
    private val beatIntervals = mutableListOf<Double>()

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
        val recordAndBuffer = createAudioRecord()
        if (recordAndBuffer == null) {
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

        val (record, bufferSize) = recordAndBuffer
        highPass1 = Biquad.highPass(sampleRate.toDouble(), 600.0, 0.707)
        highPass2 = Biquad.highPass(sampleRate.toDouble(), 600.0, 0.707)
        lowPass1 = Biquad.lowPass(sampleRate.toDouble(), 5000.0, 0.707)
        lowPass2 = Biquad.lowPass(sampleRate.toDouble(), 5000.0, 0.707)

        val buffer = ShortArray(maxOf(1024, bufferSize / 4))
        val halfBeatGuessMs = initialGuessPeriodMs / 2.0
        var refractoryMs = (halfBeatGuessMs * 0.4).coerceIn(60.0, 1_600.0).toLong()

        record.startRecording()
        try {
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                refractoryMs = processAudioBlock(buffer, read, refractoryMs)
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
            record.release()
        }
    }

    private fun createAudioRecord(): Pair<AudioRecord, Int>? {
        for (candidateRate in listOf(192_000, 96_000, 48_000, 44_100)) {
            val minBuffer = AudioRecord.getMinBufferSize(
                candidateRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) continue

            try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    candidateRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer * 2, 4096)
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    sampleRate = candidateRate
                    return record to maxOf(minBuffer * 2, 4096)
                }
                record.release()
            } catch (_: IllegalArgumentException) {
            }
        }
        return null
    }

    private fun processAudioBlock(data: ShortArray, count: Int, refractoryMs: Long): Long {
        var currentRefractoryMs = lockedRefractoryMs ?: refractoryMs
        val nowNs = System.nanoTime()
        for (i in 0 until count) {
            val sample = data[i] / 32768.0
            val bp = lowPass2.process(lowPass1.process(highPass2.process(highPass1.process(sample))))
            val rectified = abs(bp)

            val rawAttack = 0.45
            val rawDecay = 0.015
            rawEnvelope = if (rectified > rawEnvelope) {
                rawAttack * rectified + (1.0 - rawAttack) * rawEnvelope
            } else {
                rawDecay * rectified + (1.0 - rawDecay) * rawEnvelope
            }

            if (lockedRefractoryMs != null) {
                val targetGain = when {
                    rawEnvelope <= 0.005 -> 8.0
                    rawEnvelope >= 0.040 -> 1.0
                    else -> 8.0 - (rawEnvelope - 0.005) * 7.0 / 0.035
                }
                inputGain += (targetGain - inputGain) * 0.00001
            } else {
                inputGain = 1.0
            }

            // Fast attack, slower decay envelope.
            val attack = 0.45
            val decay = 0.015
            val amplifiedRectified = rectified * inputGain
            envelope = if (amplifiedRectified > envelope) {
                attack * amplifiedRectified + (1.0 - attack) * envelope
            } else {
                decay * amplifiedRectified + (1.0 - decay) * envelope
            }

            noiseFloor = 0.001 * envelope + 0.999 * noiseFloor
            val threshold = noiseFloor * 3.5 + 0.003
            val risingCross = prevEnvelope < threshold && envelope >= threshold
            prevEnvelope = envelope

            val sampleTimeNs = nowNs + (i * 1_000_000_000L / sampleRate)
            if (risingCross && !peakActive) {
                peakActive = true
                peakEnvelope = envelope
                peakTimeNs = sampleTimeNs
            } else if (peakActive) {
                if (envelope > peakEnvelope) {
                    peakEnvelope = envelope
                    peakTimeNs = sampleTimeNs
                }
                if (envelope <= peakEnvelope * 0.80) {
                    peakActive = false
                    val beatTimeNs = peakTimeNs
                    if (!startupPeakDiscarded) {
                        startupPeakDiscarded = true
                        continue
                    }
                    if (lastBeatTimeNs != 0L &&
                        (beatTimeNs - lastBeatTimeNs) / 1_000_000 < currentRefractoryMs
                    ) {
                        continue
                    }

                    beatCount += 1
                    val beatLabel = if (beatCount % 2 == 1) 'L' else 'R'
                    if (firstBeatTimeNs == 0L) firstBeatTimeNs = beatTimeNs
                    val beatTimeSeconds = (beatTimeNs - firstBeatTimeNs) / 1_000_000_000.0

                    if (lastBeatTimeNs != 0L) {
                        val deltaMs = (beatTimeNs - lastBeatTimeNs) / 1_000_000.0
                        if (deltaMs in 120.0..4_000.0) beatIntervals += deltaMs
                        if (lockedRefractoryMs == null && deltaMs in 120.0..4_000.0) {
                            currentRefractoryMs = fitRefractoryMs()
                        }
                        if (lastBeatLabel == 'L' && beatLabel == 'R') {
                            addDuration(lDurations, deltaMs)
                        } else if (lastBeatLabel == 'R' && beatLabel == 'L') {
                            addDuration(rDurations, deltaMs)
                        }
                    }

                    lastBeatTimeNs = beatTimeNs
                    lastBeatLabel = beatLabel
                    if (
                        lockedRefractoryMs == null &&
                        (beatCount >= maxRefinementBeats ||
                            (beatCount >= minRefinementBeats && isFitStable()))
                    ) {
                        lockedRefractoryMs = currentRefractoryMs
                    }
                    emitMetrics(beatLabel, beatTimeSeconds)
                }
            }
        }
        return lockedRefractoryMs ?: currentRefractoryMs
    }

    private fun fitRefractoryMs(): Long {
        val recentIntervals = beatIntervals.takeLast(12).sorted()
        if (recentIntervals.isEmpty()) return 60L
        val middle = recentIntervals.size / 2
        val median = if (recentIntervals.size % 2 == 0) {
            (recentIntervals[middle - 1] + recentIntervals[middle]) / 2.0
        } else {
            recentIntervals[middle]
        }
        return (median * 0.4).coerceIn(60.0, 1_600.0).toLong()
    }

    private fun isFitStable(): Boolean {
        if (beatIntervals.size < 8) return false
        val recentIntervals = beatIntervals.takeLast(8)
        val firstHalf = recentIntervals.take(4).sorted()
        val secondHalf = recentIntervals.takeLast(4).sorted()
        val firstMedian = (firstHalf[1] + firstHalf[2]) / 2.0
        val secondMedian = (secondHalf[1] + secondHalf[2]) / 2.0
        return abs(firstMedian - secondMedian) / maxOf(firstMedian, secondMedian) <= 0.02
    }

    private fun addDuration(queue: ArrayDeque<Double>, value: Double) {
        if (value < 120 || value > 4_000) return
        queue.addLast(value)
        while (queue.size > 14) queue.removeFirst()
    }

    private fun emitMetrics(label: Char, beatTimeSeconds: Double) {
        val l = if (lDurations.isNotEmpty()) lDurations.average() else 0.0
        val r = if (rDurations.isNotEmpty()) rDurations.average() else 0.0
        val acquiring = lDurations.size < 3 || rDurations.size < 3 || lockedRefractoryMs == null

        if (l > 0.0 && r > 0.0) {
            val period = l + r
            val asymMs = l - r
            val asymPct = 100.0 * asymMs / period
            val meanPeriodStats = calculateMeanPeriodStats(beatIntervals, trimEnd = false)
            onMetrics(
                BeatMetrics(
                    period,
                    asymMs,
                    asymPct,
                    label,
                    acquiring,
                    beatTimeSeconds,
                    meanPeriodStats?.meanPeriodMs ?: 0.0,
                    meanPeriodStats?.standardDeviationMs ?: 0.0
                )
            )
        } else {
            onMetrics(BeatMetrics(0.0, 0.0, 0.0, label, true, beatTimeSeconds))
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
