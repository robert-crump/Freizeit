package com.example.freizeit.ui.explore

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Key used for [CATEGORY_ICONS] and the registered marker bitmap when a POI's category isn't
 *  one of [com.example.freizeit.ui.common.CATEGORY_ORDER]'s known values. */
const val UNKNOWN_CATEGORY = "unknown"

/** Registered MapLibre image name for a category's marker bitmap. */
fun markerIconId(category: String): String = "marker-$category"

/** Same icon shown on a POI's map marker, exposed for the Explore chip row's leading icon. */
fun categoryIcon(category: String): ImageVector = CATEGORY_ICONS[category] ?: CATEGORY_ICONS.getValue(UNKNOWN_CATEGORY)

/** One Material icon per known POI category, plus a generic fallback for anything unmapped. */
private val CATEGORY_ICONS: Map<String, ImageVector> = mapOf(
    "playground" to Icons.Filled.ChildCare,
    "park" to Icons.Filled.Park,
    "cafe" to Icons.Filled.LocalCafe,
    "restaurant" to Icons.Filled.Restaurant,
    "ice_cream" to Icons.Filled.Icecream,
    "shop" to Icons.Filled.Storefront,
    "tourism" to Icons.Filled.Attractions,
    "leisure_other" to Icons.Filled.Interests,
    "office" to Icons.Filled.Business,
    "craft" to Icons.Filled.Handyman,
    "historic" to Icons.Filled.Museum,
    UNKNOWN_CATEGORY to Icons.Filled.Place
)

/** Marker circle diameter — 25% larger than the original plain-color CircleLayer's 24px
 *  (12px radius), for icon legibility. Icon size scales with it via [ICON_SCALE]. */
private val MARKER_DIAMETER = 30.dp
private val MARKER_STROKE_WIDTH = 1.5.dp

/** Fraction of the circle's diameter the icon glyph occupies, leaving room for the stroke. */
private const val ICON_SCALE = 0.6f

/**
 * Builds one "circle + category icon" bitmap per entry in [CATEGORY_ICONS], themed for
 * [darkTheme] (background/stroke/icon-tint per [markerBackgroundColor]/[markerForegroundColor]).
 * Keyed by category (including [UNKNOWN_CATEGORY]) so callers can register each with MapLibre via
 * `style.addImage(markerIconId(category), bitmap)`.
 */
@Composable
fun rememberMarkerBitmaps(darkTheme: Boolean): Map<String, Bitmap> {
    val density = LocalDensity.current
    val painters = CATEGORY_ICONS.mapValues { (_, icon) -> rememberVectorPainter(icon) }
    val backgroundColor = markerBackgroundColor(darkTheme)
    val foregroundColor = markerForegroundColor(darkTheme)
    return remember(painters, backgroundColor, foregroundColor, density) {
        val diameterPx = with(density) { MARKER_DIAMETER.toPx() }
        val strokePx = with(density) { MARKER_STROKE_WIDTH.toPx() }
        val iconSizePx = diameterPx * ICON_SCALE
        painters.mapValues { (_, painter) ->
            drawMarkerBitmap(painter, density, diameterPx, strokePx, iconSizePx, backgroundColor, foregroundColor)
        }
    }
}

private fun drawMarkerBitmap(
    painter: VectorPainter,
    density: Density,
    diameterPx: Float,
    strokePx: Float,
    iconSizePx: Float,
    backgroundColor: Color,
    foregroundColor: Color
): Bitmap {
    val sizePx = diameterPx.toInt().coerceAtLeast(1)
    val imageBitmap = ImageBitmap(sizePx, sizePx)
    val canvas = Canvas(imageBitmap)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(sizePx.toFloat(), sizePx.toFloat())) {
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val fillRadius = sizePx / 2f - strokePx / 2f
        drawCircle(color = backgroundColor, radius = fillRadius, center = center)
        drawCircle(color = foregroundColor, radius = fillRadius, center = center, style = Stroke(width = strokePx))
        val iconOffset = (sizePx - iconSizePx) / 2f
        translate(left = iconOffset, top = iconOffset) {
            with(painter) {
                draw(size = Size(iconSizePx, iconSizePx), colorFilter = ColorFilter.tint(foregroundColor))
            }
        }
    }
    return imageBitmap.asAndroidBitmap()
}
