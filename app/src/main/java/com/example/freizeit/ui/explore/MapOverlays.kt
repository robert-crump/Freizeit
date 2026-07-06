package com.example.freizeit.ui.explore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.max

/**
 * Draws a location as a filled dot with a white ring, plus an optional translucent
 * accuracy circle sized from GPS accuracy (meters) — the "you are here" style used
 * across the app's maps.
 */
class DotOverlay(
    private val point: GeoPoint,
    private val fillColor: Int,
    private val accuracyMeters: Float? = null
) : Overlay() {

    private val dotRadiusPx = 9f
    private val screenPoint = Point()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (fillColor and 0x00FFFFFF) or 0x33000000
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        projection.toPixels(point, screenPoint)

        if (accuracyMeters != null) {
            val latRad = Math.toRadians(point.latitude)
            val equatorPixels = projection.metersToEquatorPixels(accuracyMeters)
            val radiusPx = equatorPixels / max(cos(latRad).toFloat(), 0.2f)
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radiusPx, accuracyPaint)
        }

        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), dotRadiusPx, fillPaint)
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), dotRadiusPx, strokePaint)
    }
}
