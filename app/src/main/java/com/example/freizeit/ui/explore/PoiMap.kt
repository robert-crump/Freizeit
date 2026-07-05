package com.example.freizeit.ui.explore

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.ui.common.categoryColor
import com.example.freizeit.util.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme

private const val FALLBACK_LAT = 50.94 // Cologne area, center of the extract coverage
private const val FALLBACK_LON = 6.96

/**
 * osmdroid map showing the filtered POIs as fast point overlays, one per
 * category so each keeps its color. POIs come from Room; only the tiles
 * need network.
 */
@Composable
fun PoiMap(
    pois: List<PoiWithDistance>,
    location: LatLon?,
    onPoiClick: (PoiWithDistance) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(
                if (location != null) GeoPoint(location.lat, location.lon)
                else GeoPoint(FALLBACK_LAT, FALLBACK_LON)
            )
        }
    }
    // The overlays currently on the map, so update can replace them cheaply.
    val overlayState = remember { MapOverlayState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            if (overlayState.renderedPois !== pois) {
                overlayState.renderedPois = pois
                it.overlays.removeAll(overlayState.overlays)
                overlayState.overlays.clear()
                pois.groupBy { p -> p.poi.category }.forEach { (category, group) ->
                    overlayState.overlays += buildCategoryOverlay(category, group, onPoiClick)
                }
                it.overlays.addAll(overlayState.overlays)
                it.invalidate()
            }
        }
    )
}

private class MapOverlayState {
    var renderedPois: List<PoiWithDistance>? = null
    val overlays = mutableListOf<SimpleFastPointOverlay>()
}

private fun buildCategoryOverlay(
    category: String,
    pois: List<PoiWithDistance>,
    onPoiClick: (PoiWithDistance) -> Unit
): SimpleFastPointOverlay {
    val points = pois.map { LabelledGeoPoint(it.poi.lat, it.poi.lon, it.poi.name ?: "") }
    val style = Paint().apply {
        style = Paint.Style.FILL
        color = categoryColor(category).toArgb()
    }
    val options = SimpleFastPointOverlayOptions.getDefaultStyle()
        .setAlgorithm(
            if (points.size > 5000) SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION
            else SimpleFastPointOverlayOptions.RenderingAlgorithm.MEDIUM_OPTIMIZATION
        )
        .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
        .setPointStyle(style)
        .setRadius(9f)
        .setIsClickable(true)
        .setCellSize(12)
    return SimpleFastPointOverlay(SimplePointTheme(points, false), options).apply {
        setOnClickListener { _, pointIndex -> onPoiClick(pois[pointIndex]) }
    }
}
