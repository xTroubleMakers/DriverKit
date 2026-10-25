package id.driverkit.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.text.NumberFormat
import java.util.Locale

/** Bar chart ringan: batang terakhir disorot penuh. */
class BarChartView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var data: List<Pair<String, Long>> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC7D2FE.toInt() }
    private val lastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4F46E5.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFF94A3B8.toInt()
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFF4338CA.toInt()
    }

    fun setData(d: List<Pair<String, Long>>) {
        data = d
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val max = data.maxOf { it.second }.coerceAtLeast(1L)
        val w = width.toFloat()
        val h = height.toFloat()
        val padTop = 40f
        val padBottom = 70f
        val usable = (h - padTop - padBottom).coerceAtLeast(1f)
        val slot = w / data.size
        val barW = slot * 0.6f

        val rp = NumberFormat.getNumberInstance(Locale("in", "ID"))

        data.forEachIndexed { i, (label, v) ->
            val left = i * slot + (slot - barW) / 2f
            val bh = (v.toFloat() / max) * usable
            val rect = RectF(left, padTop + (usable - bh), left + barW, padTop + usable)
            canvas.drawRoundRect(
                rect, 8f, 8f,
                if (i == data.lastIndex) lastPaint else barPaint
            )

            canvas.drawText(label, left + barW / 2f, h - 20f, labelPaint)

            if (v > 0) {
                canvas.drawText(
                    rp.format(v / 1000) + "rb",
                    left + barW / 2f,
                    padTop + (usable - bh) - 12f,
                    valuePaint
                )
            }
        }
    }
}
