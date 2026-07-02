package com.ticktock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 12f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A46B3")
        textSize = 96f
        textAlign = Paint.Align.CENTER
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E9F45")
        strokeWidth = 24f
        style = Paint.Style.STROKE
    }

    private var asymmetryPercent: Double = 0.0

    fun setAsymmetryPercent(value: Double) {
        asymmetryPercent = value.coerceIn(-25.0, 25.0)
        val a = abs(asymmetryPercent)
        arrowPaint.color = when {
            a > 10.0 -> Color.parseColor("#CC2B2B")
            a >= 2.5 -> Color.parseColor("#E2B400")
            else -> Color.parseColor("#1E9F45")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val indicatorDrop = textPaint.textSize / 3f

        val margin = w * 0.10f
        val baseline = h * 0.54f
        val minX = margin
        val maxX = w - margin
        val centerX = w / 2f
        val labelY = baseline + (h * 0.24f) + indicatorDrop

        canvas.drawLine(minX, baseline, maxX, baseline, linePaint)
        canvas.drawLine(centerX, baseline - 24f, centerX, baseline + 24f, linePaint)

        canvas.drawText("-25%", minX + 54f, labelY, textPaint)
        canvas.drawText("0%", centerX, labelY, textPaint)
        canvas.drawText("+25%", maxX - 54f, labelY, textPaint)

        val normalized = ((asymmetryPercent + 25.0) / 50.0).toFloat()
        val arrowX = minX + (maxX - minX) * normalized
        val tipY = max(16f, baseline - (h * 0.02f))
        val headHeight = 52f
        val stemTopY = max(16f, baseline - (h * 0.90f))

        canvas.drawLine(arrowX, stemTopY, arrowX, tipY - headHeight, arrowPaint)

        val arrowHead = Path().apply {
            moveTo(arrowX - 36f, tipY - headHeight)
            lineTo(arrowX + 36f, tipY - headHeight)
            lineTo(arrowX, tipY)
            close()
        }
        canvas.drawPath(arrowHead, arrowPaint.apply { style = Paint.Style.FILL })
        arrowPaint.style = Paint.Style.STROKE
    }
}
