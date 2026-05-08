package com.example.volatilestocks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
    }

    private var values: List<Double> = emptyList()

    fun setValues(newValues: List<Double>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#111111"))

        if (values.isEmpty()) {
            canvas.drawText("No chart data", 40f, height / 2f, textPaint)
            return
        }

        val left = 40f
        val top = 20f
        val right = width - 20f
        val bottom = height - 30f

        for (i in 0..4) {
            val y = top + ((bottom - top) / 4f) * i
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        var minVal = values.first()
        var maxVal = values.first()
        values.forEach {
            minVal = min(minVal, it)
            maxVal = max(maxVal, it)
        }
        val range = if ((maxVal - minVal) == 0.0) 1.0 else (maxVal - minVal)
        val stepX = if (values.size == 1) 1f else (right - left) / (values.size - 1)

        for (i in 0 until values.size - 1) {
            val x1 = left + i * stepX
            val x2 = left + (i + 1) * stepX
            val y1 = bottom - (((values[i] - minVal) / range) * (bottom - top)).toFloat()
            val y2 = bottom - (((values[i + 1] - minVal) / range) * (bottom - top)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        canvas.drawText(String.format("Low %.2f", minVal), left, bottom + 25f, textPaint)
        canvas.drawText(String.format("High %.2f", maxVal), right - 180f, bottom + 25f, textPaint)
    }
}
