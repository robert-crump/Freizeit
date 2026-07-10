package com.example.freizeit.ui.explore

import androidx.compose.ui.graphics.toArgb
import com.example.freizeit.ui.common.CATEGORY_ORDER
import com.example.freizeit.ui.common.categoryColor
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

private const val BASEMAP_SOURCE_ID = "carto-dark-matter"
private const val BASEMAP_LAYER_ID = "carto-dark-matter-layer"
private const val BASEMAP_TILE_SIZE = 256

/** CARTO's dark basemap, raster tiles — same source used before the MapLibre migration. */
fun cartoDarkMatterSource(): RasterSource {
    val tileSet = TileSet(
        "2.1.0",
        "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        "https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        "https://c.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        "https://d.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
    ).apply {
        attribution = "© OpenStreetMap contributors © CARTO"
    }
    return RasterSource(BASEMAP_SOURCE_ID, tileSet, BASEMAP_TILE_SIZE)
}

fun cartoDarkMatterLayer(): RasterLayer = RasterLayer(BASEMAP_LAYER_ID, BASEMAP_SOURCE_ID)

/** Data-driven `circle-color` expression matching [categoryColor] for every known category. */
fun categoryColorExpression(): Expression {
    val fallback = Expression.color(categoryColor("").toArgb())
    val stops = CATEGORY_ORDER.map { category ->
        Expression.stop(category, Expression.color(categoryColor(category).toArgb()))
    }.toTypedArray()
    return Expression.match(Expression.get("category"), fallback, *stops)
}
