package com.example.freizeit.ui.explore

import androidx.compose.ui.graphics.Color
import com.example.freizeit.ui.common.CATEGORY_ORDER
import org.maplibre.android.style.expressions.Expression

/**
 * CARTO's dark basemap, vector GL style (same one used by the Velometrics app) — replaces the
 * old raster `dark_all` tiles. Vector rendering keeps roads/labels legible at more zoom levels,
 * and the style ships its own `glyphs` config, so text layers (e.g. cluster counts) render
 * without any extra font setup.
 */
const val DARK_MATTER_STYLE_URL = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

/** CARTO's light counterpart to [DARK_MATTER_STYLE_URL], paired with the app's light theme. */
const val POSITRON_STYLE_URL = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

/** Which basemap style to load for the given theme; read once at map creation, not live-switched. */
fun mapStyleUrl(darkTheme: Boolean): String = if (darkTheme) DARK_MATTER_STYLE_URL else POSITRON_STYLE_URL

/** Fixed dark blue for the "you are here" marker — deliberately not theme-derived so it stays a
 *  consistent, recognizable color distinct from any POI category color, in both light and dark mode. */
const val POSITION_DOT_COLOR: Int = 0xFF0D47A1.toInt()

// Marker circle chrome (background, stroke, icon tint) is theme-driven, independent of the
// app's dynamic Material color scheme, so markers read consistently regardless of device.
private val MARKER_BACKGROUND_DARK = Color(0xFF303030)
private val MARKER_FOREGROUND_DARK = Color(0xFFFAFAFA)
private val MARKER_BACKGROUND_LIGHT = Color(0xFFFFFFFF)
private val MARKER_FOREGROUND_LIGHT = Color(0xFF212121)

/** Marker circle fill: dark theme -> dark circle, light theme -> light circle. */
fun markerBackgroundColor(darkTheme: Boolean): Color =
    if (darkTheme) MARKER_BACKGROUND_DARK else MARKER_BACKGROUND_LIGHT

/** Marker stroke + icon tint: always the opposite of [markerBackgroundColor], for contrast. */
fun markerForegroundColor(darkTheme: Boolean): Color =
    if (darkTheme) MARKER_FOREGROUND_DARK else MARKER_FOREGROUND_LIGHT

/** Data-driven `icon-image` expression mapping category -> registered marker bitmap name. */
fun categoryIconExpression(): Expression {
    val fallback = Expression.literal(markerIconId(UNKNOWN_CATEGORY))
    val stops = CATEGORY_ORDER.map { category ->
        Expression.stop(category, Expression.literal(markerIconId(category)))
    }.toTypedArray()
    return Expression.match(Expression.get("category"), fallback, *stops)
}
