package com.ledapp.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Kreisförmiger HSV-Farbwähler (Hue/Saturation). Value ist immer 255 -
 * die tatsächliche LED-Helligkeit wird separat über den Brightness-Slider
 * geregelt (entspricht FastLED.setBrightness auf dem Arduino).
 */
class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnColorChangeListener {
        fun onColorChanged(r: Int, g: Int, b: Int, finalValue: Boolean)
    }

    var listener: OnColorChangeListener? = null

    private var wheelBitmap: Bitmap? = null
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    private val markerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    // Aktuelle Auswahl
    private var hue = 0f          // 0..360
    private var sat = 0f          // 0..1
    var currentColor: Int = Color.RED
        private set

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f - 8f
        buildWheelBitmap()
    }

    private fun buildWheelBitmap() {
        val size = (radius * 2).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hsv = FloatArray(3)
        hsv[2] = 1f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - size / 2f
                val dy = y - size / 2f
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= size / 2f) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    hsv[0] = angle
                    hsv[1] = (dist / (size / 2f)).coerceIn(0f, 1f)
                    bmp.setPixel(x, y, Color.HSVToColor(hsv))
                } else {
                    bmp.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
        wheelBitmap = bmp
        updateCurrentColorFromHueSat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = wheelBitmap ?: return
        val dst = RectF(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius
        )
        canvas.drawBitmap(bmp, null, dst, null)

        // Marker an der aktuell gewählten Position zeichnen
        val angleRad = Math.toRadians(hue.toDouble())
        val dist = sat * radius
        val mx = centerX + (dist * kotlin.math.cos(angleRad)).toFloat()
        val my = centerY + (dist * kotlin.math.sin(angleRad)).toFloat()

        markerFillPaint.color = currentColor
        canvas.drawCircle(mx, my, 22f, markerFillPaint)
        canvas.drawCircle(mx, my, 22f, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dx = event.x - centerX
        val dy = event.y - centerY
        var dist = sqrt(dx * dx + dy * dy)
        if (dist > radius) dist = radius

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f

        hue = angle
        sat = (dist / radius).coerceIn(0f, 1f)
        updateCurrentColorFromHueSat()
        invalidate()

        val isFinal = event.action == MotionEvent.ACTION_UP
        val r = Color.red(currentColor)
        val g = Color.green(currentColor)
        val b = Color.blue(currentColor)
        listener?.onColorChanged(r, g, b, isFinal)

        return true
    }

    private fun updateCurrentColorFromHueSat() {
        val hsv = floatArrayOf(hue, sat, 1f)
        currentColor = Color.HSVToColor(hsv)
    }

    /** Setzt die Markerposition anhand einer RGB-Farbe (z.B. beim Programmstart). */
    fun setColor(r: Int, g: Int, b: Int) {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        hue = hsv[0]
        sat = hsv[1]
        currentColor = Color.rgb(r, g, b)
        invalidate()
    }
}
