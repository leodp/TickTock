package com.ticktock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        textSize = 72f
        textAlign = Paint.Align.CENTER
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E9F45")
        strokeWidth = 20f
        style = Paint.Style.FILL_AND_STROKE
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

        val margin = 28f
        val baseline = h * 0.62f
        val minX = margin
        val maxX = w - margin
        val centerX = w / 2f

        canvas.drawLine(minX, baseline, maxX, baseline, linePaint)
        canvas.drawLine(centerX, baseline - 24f, centerX, baseline + 24f, linePaint)

        canvas.drawText("-25%", minX + 54f, baseline - 34f, textPaint)
        canvas.drawText("0%", centerX, baseline - 34f, textPaint)
        canvas.drawText("+25%", maxX - 54f, baseline - 34f, textPaint)

        val normalized = ((asymmetryPercent + 25.0) / 50.0).toFloat()
        val arrowX = minX + (maxX - minX) * normalized
        val topY = max(18f, baseline - 200f)

        canvas.drawLine(arrowX, topY, arrowX, baseline - 14f, arrowPaint)
        canvas.drawLine(arrowX, topY, arrowX - 28f, topY + 38f, arrowPaint)
        canvas.drawLine(arrowX, topY, arrowX + 28f, topY + 38f, arrowPaint)
    }
}
