package com.example.freizeit.ui.explore

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme

private const val FALLBACK_LAT = 50.94 // Cologne area, center of the extract coverage
private const val FALLBACK_LON = 6.96
private const val LOCATE_ME_ZOOM = 16.0

/** CARTO's dark basemap, raster tiles so it drops straight into osmdroid's tile source API. */
val CARTO_DARK_MATTER = XYTileSource(
    "CartoDarkMatter",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

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
    recenterRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationDotColor = MaterialTheme.colorScheme.primary.toArgb()

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(CARTO_DARK_MATTER)
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(
                if (location != null) GeoPoint(location.lat, location.lon)
                else GeoPoint(FALLBACK_LAT, FALLBACK_LON)
            )
            overlays.add(CopyrightOverlay(context))
        }
    }
    // The overlays currently on the map, so update can replace them cheaply.
    val overlayState = remember { MapOverlayState() }

    // Bumped by the "locate me" FAB; only recenter when it actually changes and a fix is available.
    var lastHandledRecenter by remember { mutableIntStateOf(0) }
    LaunchedEffect(recenterRequest, location) {
        if (recenterRequest != 0 && recenterRequest != lastHandledRecenter && location != null) {
            mapView.controller.animateTo(GeoPoint(location.lat, location.lon))
            mapView.controller.setZoom(LOCATE_ME_ZOOM)
            lastHandledRecenter = recenterRequest
        }
    }

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
        modifier = modifier.clipToBounds(),
        update = { view ->
            var changed = false
            if (overlayState.renderedPois !== pois) {
                overlayState.renderedPois = pois
                view.overlays.removeAll(overlayState.poiOverlays)
                overlayState.poiOverlays.clear()
                pois.groupBy { p -> p.poi.category }.forEach { (category, group) ->
                    overlayState.poiOverlays += buildCategoryOverlay(category, group, onPoiClick)
                }
                view.overlays.addAll(overlayState.poiOverlays)
                changed = true
            }
            if (overlayState.renderedLocation != location) {
                overlayState.renderedLocation = location
                overlayState.locationOverlay?.let(view.overlays::remove)
                overlayState.locationOverlay = location?.let { loc ->
                    DotOverlay(GeoPoint(loc.lat, loc.lon), locationDotColor, loc.accuracyMeters)
                }
                changed = true
            } else if (changed) {
                // POI overlays were just rebuilt underneath; re-append the dot so it stays on top.
                overlayState.locationOverlay?.let(view.overlays::remove)
            }
            if (changed) {
                overlayState.locationOverlay?.let(view.overlays::add)
                view.invalidate()
            }
        }
    )
}

private class MapOverlayState {
    var renderedPois: List<PoiWithDistance>? = null
    val poiOverlays = mutableListOf<SimpleFastPointOverlay>()
    var renderedLocation: LatLon? = null
    var locationOverlay: DotOverlay? = null
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
