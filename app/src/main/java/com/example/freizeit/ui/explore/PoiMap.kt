package com.example.freizeit.ui.explore

import android.graphics.Paint
import android.graphics.Point
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
import com.example.freizeit.ui.common.categoryColor
import com.example.freizeit.util.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.simplefastpoint.LabelledGeoPoint
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme

private const val FALLBACK_LAT = 50.94 // Cologne area, center of the extract coverage
private const val FALLBACK_LON = 6.96
private const val DEFAULT_ZOOM = 12.0
private const val LOCATE_ME_ZOOM = 16.0

/** Below this zoom, individual POIs are grouped into count-badged clusters instead of pins. */
private const val CLUSTER_ZOOM_THRESHOLD = 14.0
private const val CLUSTER_GRID_DP = 60
private const val CLUSTER_TAP_ZOOM_STEP = 3.0

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
 * osmdroid map showing the filtered POIs as fast point overlays, one per category so
 * each keeps its color — grouped into count-badged clusters below [CLUSTER_ZOOM_THRESHOLD]
 * so the map stays readable when zoomed out. POIs come from Room; only the tiles need
 * network.
 */
@Composable
fun PoiMap(
    pois: List<PoiWithDistance>,
    location: LatLon?,
    onPoiClick: (PoiWithDistance) -> Unit,
    customNames: Map<String, String> = emptyMap(),
    recenterRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationDotColor = MaterialTheme.colorScheme.primary.toArgb()
    val density = context.resources.displayMetrics.density

    // The overlays currently on the map, so update can replace them cheaply.
    val overlayState = remember { MapOverlayState() }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(CARTO_DARK_MATTER)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(
                if (location != null) GeoPoint(location.lat, location.lon)
                else GeoPoint(FALLBACK_LAT, FALLBACK_LON)
            )
            overlays.add(CopyrightOverlay(context))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    refreshPoiOverlays(this@apply, overlayState)
                    return false
                }
            })
            // Clustering needs real screen-pixel projection math, which isn't valid
            // until the view has been measured — redo the initial grouping once it has.
            addOnFirstLayoutListener { _, _, _, _, _ -> refreshPoiOverlays(this@apply, overlayState) }
        }
    }

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
            if (overlayState.renderedPois != pois || overlayState.renderedCustomNames != customNames) {
                overlayState.renderedPois = pois
                overlayState.renderedCustomNames = customNames
                overlayState.density = density
                overlayState.onPoiClick = onPoiClick
                refreshPoiOverlays(view, overlayState)
                changed = true
            }
            if (overlayState.renderedLocation != location) {
                overlayState.renderedLocation = location
                overlayState.locationOverlay?.let(view.overlays::remove)
                overlayState.locationOverlay = location?.let { loc ->
                    DotOverlay(GeoPoint(loc.lat, loc.lon), locationDotColor, loc.accuracyMeters, density)
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
    var renderedCustomNames: Map<String, String>? = null
    var density: Float = 1f
    var onPoiClick: (PoiWithDistance) -> Unit = {}
    val poiOverlays = mutableListOf<SimpleFastPointOverlay>()
    var clusterOverlay: ClusterOverlay? = null
    var renderedLocation: LatLon? = null
    var locationOverlay: DotOverlay? = null
}

/**
 * Swaps between individual per-category pins and grid-clustered bubbles depending on
 * the map's current zoom. Called both when the POI set changes and, imperatively via
 * [MapListener], whenever the zoom level changes — clustering has to react to zoom
 * even though that isn't Compose state.
 */
private fun refreshPoiOverlays(mapView: MapView, state: MapOverlayState) {
    mapView.overlays.removeAll(state.poiOverlays)
    state.poiOverlays.clear()
    state.clusterOverlay?.let(mapView.overlays::remove)
    state.clusterOverlay = null

    val pois = state.renderedPois ?: emptyList()
    if (mapView.zoomLevelDouble < CLUSTER_ZOOM_THRESHOLD) {
        val cellPx = (CLUSTER_GRID_DP * state.density).toInt().coerceAtLeast(1)
        val clusters = clusterPois(pois, mapView, cellPx)
        val overlay = ClusterOverlay(clusters, state.density) { point ->
            mapView.controller.animateTo(point)
            mapView.controller.setZoom(mapView.zoomLevelDouble + CLUSTER_TAP_ZOOM_STEP)
        }
        state.clusterOverlay = overlay
        mapView.overlays.add(overlay)
    } else {
        pois.groupBy { p -> p.poi.category }.forEach { (category, group) ->
            state.poiOverlays += buildCategoryOverlay(
                category, group, state.density, state.renderedCustomNames ?: emptyMap(), state.onPoiClick
            )
        }
        mapView.overlays.addAll(state.poiOverlays)
    }
    // POI overlays were just rebuilt underneath; re-append the dot so it stays on top.
    state.locationOverlay?.let(mapView.overlays::remove)
    state.locationOverlay?.let(mapView.overlays::add)
    mapView.invalidate()
}

/**
 * Grid-buckets POIs in screen-pixel space (so the grouping radius reads the same on
 * every device) and collapses each bucket to its centroid, colored by whichever
 * category is most common in that bucket.
 */
private fun clusterPois(
    pois: List<PoiWithDistance>,
    mapView: MapView,
    cellSizePx: Int
): List<ClusterOverlay.Cluster> {
    if (pois.isEmpty()) return emptyList()
    val projection = mapView.projection
    val screenPoint = Point()
    val buckets = LinkedHashMap<Pair<Int, Int>, MutableList<PoiWithDistance>>()
    pois.forEach { p ->
        projection.toPixels(GeoPoint(p.poi.lat, p.poi.lon), screenPoint)
        val key = Math.floorDiv(screenPoint.x, cellSizePx) to Math.floorDiv(screenPoint.y, cellSizePx)
        buckets.getOrPut(key) { mutableListOf() }.add(p)
    }
    return buckets.values.map { group ->
        val avgLat = group.sumOf { it.poi.lat } / group.size
        val avgLon = group.sumOf { it.poi.lon } / group.size
        val dominantCategory = group.groupingBy { it.poi.category }.eachCount().maxByOrNull { it.value }!!.key
        ClusterOverlay.Cluster(
            GeoPoint(avgLat, avgLon),
            group.size,
            categoryColor(dominantCategory).toArgb()
        )
    }
}

private fun buildCategoryOverlay(
    category: String,
    pois: List<PoiWithDistance>,
    density: Float,
    customNames: Map<String, String>,
    onPoiClick: (PoiWithDistance) -> Unit
): SimpleFastPointOverlay {
    val points = pois.map {
        LabelledGeoPoint(it.poi.lat, it.poi.lon, customNames[it.poi.id] ?: it.poi.name ?: "")
    }
    val style = Paint().apply {
        style = Paint.Style.FILL
        color = categoryColor(category).toArgb()
    }
    // osmdroid draws in raw physical pixels, not dp, so scale by density to keep POI
    // circles a consistent on-screen size instead of shrinking on high-density screens.
    val options = SimpleFastPointOverlayOptions.getDefaultStyle()
        .setAlgorithm(
            if (points.size > 5000) SimpleFastPointOverlayOptions.RenderingAlgorithm.MAXIMUM_OPTIMIZATION
            else SimpleFastPointOverlayOptions.RenderingAlgorithm.MEDIUM_OPTIMIZATION
        )
        .setSymbol(SimpleFastPointOverlayOptions.Shape.CIRCLE)
        .setPointStyle(style)
        .setRadius(13f * density)
        .setIsClickable(true)
        .setCellSize((16 * density).toInt())
    return SimpleFastPointOverlay(SimplePointTheme(points, false), options).apply {
        setOnClickListener { _, pointIndex -> onPoiClick(pois[pointIndex]) }
    }
}
