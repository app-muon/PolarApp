package com.polarapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.floor

class PulseGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        alpha = 80
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
        textSize = 28f
    }

    private var bins: List<Int> = emptyList()

    fun setData(newBins: List<Int>) {
        bins = newBins
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val leftPad = 64f
        val rightPad = 8f
        val topPad = 16f
        val bottomPad = 32f

        val plotW = w - leftPad - rightPad
        val plotH = h - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return

        val hrMin = 60
        val hrMax = 180
        val hrRange = (hrMax - hrMin).toFloat()

        fun hrToY(hr: Int): Float {
            val clamped = hr.coerceIn(hrMin, hrMax)
            val ratio = (clamped - hrMin) / hrRange
            return topPad + (1f - ratio) * plotH
        }

        // Horizontal grid every 20 bpm
        for (bpm in hrMin..hrMax step 20) {
            val y = hrToY(bpm)
            canvas.drawLine(leftPad, y, leftPad + plotW, y, gridPaint)
        }

        // Y-axis labels 60, 100, 140, 180
        val labelValues = listOf(60, 100, 140, 180)
        for (label in labelValues) {
            val y = hrToY(label)
            canvas.drawText(label.toString(), 8f, y + axisPaint.textSize / 2f, axisPaint)
        }

        val axisY = topPad + plotH
        canvas.drawLine(leftPad, axisY, leftPad + plotW, axisY, axisPaint)

        if (bins.isEmpty()) return

        val totalPoints = bins.size
        val stepX = if (totalPoints <= 1) 0f else plotW / (totalPoints - 1)

        // ---- X-AXIS LABELS: MAX 6, INTEGER MINUTES, WITH VERTICAL LINES ----
        val secondsPerBin = 5f
        val totalSeconds = totalPoints * secondsPerBin
        val totalMinutesFloat = totalSeconds / 60f
        val totalMinutesInt = floor(totalMinutesFloat).toInt()

        if (totalMinutesInt >= 0) {
            val maxLabels = 6
            val spanMinutes = max(1, totalMinutesInt)
            var stepMinutes = ceil(spanMinutes / maxLabels.toFloat()).toInt()
            if (stepMinutes < 1) stepMinutes = 1

            // Always include 0m
            var minute = 0
            while (minute <= totalMinutesInt) {
                val index = ((minute * 60f) / secondsPerBin).toInt()
                if (index in 0 until totalPoints) {
                    val x = leftPad + index * stepX

                    // Vertical grid line at this minute
                    canvas.drawLine(x, topPad, x, axisY, gridPaint)

                    val label = "${minute}m"
                    val textWidth = axisPaint.measureText(label)
                    canvas.drawText(label, x - textWidth / 2f, h - 4f, axisPaint)
                }
                minute += stepMinutes
            }

            // Ensure last minute label if we don't already land on it and it’s > 0
            if (totalMinutesInt > 0 && totalMinutesInt % stepMinutes != 0) {
                val lastIndex = ((totalMinutesInt * 60f) / secondsPerBin).toInt()
                if (lastIndex in 0 until totalPoints) {
                    val x = leftPad + lastIndex * stepX
                    canvas.drawLine(x, topPad, x, axisY, gridPaint)
                    val label = "${totalMinutesInt}m"
                    val textWidth = axisPaint.measureText(label)
                    canvas.drawText(label, x - textWidth / 2f, h - 4f, axisPaint)
                }
            }
        }
        // -------------------------------------------------------------------

        // HR polyline
        var prevX = leftPad
        var prevY = hrToY(bins[0])

        for (i in 1 until totalPoints) {
            val x = leftPad + i * stepX
            val y = hrToY(bins[i])
            canvas.drawLine(prevX, prevY, x, y, linePaint)
            prevX = x
            prevY = y
        }
    }
}
