package com.example.freizeit.ui.explore

import androidx.compose.ui.graphics.toArgb
import com.example.freizeit.ui.common.CATEGORY_ORDER
import com.example.freizeit.ui.common.categoryColor
import org.maplibre.android.style.expressions.Expression

/**
 * CARTO's dark basemap, vector GL style (same one used by the Velometrics app) — replaces the
 * old raster `dark_all` tiles. Vector rendering keeps roads/labels legible at more zoom levels,
 * and the style ships its own `glyphs` config, so text layers (e.g. cluster counts) render
 * without any extra font setup.
 */
const val DARK_MATTER_STYLE_URL = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

/** Fixed dark blue for the "you are here" marker — deliberately not theme-derived so it stays a
 *  consistent, recognizable color distinct from any POI category color, in both light and dark mode. */
const val POSITION_DOT_COLOR: Int = 0xFF0D47A1.toInt()

/** Data-driven `circle-color` expression matching [categoryColor] for every known category. */
fun categoryColorExpression(): Expression {
    val fallback = Expression.color(categoryColor("").toArgb())
    val stops = CATEGORY_ORDER.map { category ->
        Expression.stop(category, Expression.color(categoryColor(category).toArgb()))
    }.toTypedArray()
    return Expression.match(Expression.get("category"), fallback, *stops)
}
