package com.polarapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

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
        alpha = 80   // faint
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

        // Layout paddings
        val leftPad = 64f
        val rightPad = 8f
        val topPad = 16f
        val bottomPad = 32f

        val plotW = w - leftPad - rightPad
        val plotH = h - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return

        // HR axis range
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

        // Y-axis labels at 60, 100, 140, 180
        val labelValues = listOf(60, 100, 140, 180)
        for (label in labelValues) {
            val y = hrToY(label)
            canvas.drawText(label.toString(), 8f, y + axisPaint.textSize / 2f, axisPaint)
        }

        // X-axis line
        val axisY = topPad + plotH
        canvas.drawLine(leftPad, axisY, leftPad + plotW, axisY, axisPaint)

        if (bins.isEmpty()) return

        // Polyline + X-axis ticks (minutes, assuming 5s per bin)
        val totalPoints = bins.size
        val stepX = if (totalPoints <= 1) 0f else plotW / (totalPoints - 1)

        // Minute ticks and optional vertical grid
        if (totalPoints > 1) {
            val secondsPerBin = 5f
            val minutesPerBin = secondsPerBin / 60f

            for (i in 0 until totalPoints) {
                val minutes = i * minutesPerBin
                if (minutes % 1f == 0f) { // whole minutes
                    val x = leftPad + i * stepX
                    // vertical faint line
                    canvas.drawLine(x, topPad, x, axisY, gridPaint)

                    val label = "${minutes.toInt()}m"
                    val textWidth = axisPaint.measureText(label)
                    canvas.drawText(label, x - textWidth / 2f, h - 4f, axisPaint)
                }
            }
        }

        // Draw HR line
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
