package com.example.freizeit.ui.explore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.max

/**
 * Draws a location as a filled dot with a white ring, plus an optional translucent
 * accuracy circle sized from GPS accuracy (meters) — the "you are here" style used
 * across the app's maps.
 *
 * osmdroid's [Canvas] draws in raw physical pixels, not dp, so [density] (from
 * `resources.displayMetrics.density`) is applied to keep the dot a consistent
 * on-screen size across devices instead of shrinking to a few dp on high-density screens.
 */
class DotOverlay(
    private val point: GeoPoint,
    private val fillColor: Int,
    private val accuracyMeters: Float? = null,
    private val density: Float = 1f
) : Overlay() {

    private val dotRadiusPx = 11f * density
    private val screenPoint = Point()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f * density
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

/**
 * A group of nearby POIs collapsed into one circle with a count label, so the map
 * stays readable when zoomed out — [PoiMap] recomputes the grouping (grid-bucketed
 * in screen pixels) whenever the zoom level or POI set changes. Tapping a cluster
 * zooms in on it rather than opening a place's detail sheet, since it isn't any one POI.
 */
class ClusterOverlay(
    private val clusters: List<Cluster>,
    density: Float,
    private val onClusterTap: (GeoPoint) -> Unit
) : Overlay() {

    data class Cluster(val point: GeoPoint, val count: Int, val color: Int)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val baseRadiusPx = 18f * density
    private val screenPoint = Point()

    private fun radiusFor(count: Int) = when {
        count >= 100 -> baseRadiusPx * 1.5f
        count >= 20 -> baseRadiusPx * 1.25f
        else -> baseRadiusPx
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        clusters.forEach { cluster ->
            projection.toPixels(cluster.point, screenPoint)
            val radius = radiusFor(cluster.count)
            fillPaint.color = cluster.color
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, fillPaint)
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, strokePaint)
            textPaint.textSize = radius
            val textY = screenPoint.y - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(cluster.count.toString(), screenPoint.x.toFloat(), textY, textPaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        for (cluster in clusters) {
            projection.toPixels(cluster.point, screenPoint)
            val radius = radiusFor(cluster.count)
            val dx = e.x - screenPoint.x
            val dy = e.y - screenPoint.y
            if (dx * dx + dy * dy <= radius * radius) {
                onClusterTap(cluster.point)
                return true
            }
        }
        return false
    }
}
