package com.example.freizeit.ui.explore

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.freizeit.util.LatLon
import com.google.gson.JsonObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

// Aachen, center of the POI extraction bbox (tools/poi_extraction, Aachen +/-20km) —
// not Cologne; the app has no POI data outside this box, so the fallback must stay inside it.
private const val FALLBACK_LAT = 50.7753
private const val FALLBACK_LON = 6.0839
private const val DEFAULT_ZOOM = 12.0
private const val LOCATE_ME_ZOOM = 16.0
private const val SEARCH_FOCUS_ZOOM = 16.0

/** Below this zoom, MapLibre's built-in GeoJSON clustering groups POIs into size-tiered bubbles. */
private const val CLUSTER_MAX_ZOOM = 12
private const val CLUSTER_RADIUS = 60
private const val CLUSTER_TAP_ZOOM_STEP = 3.0

/** Screen-space tolerance around a tap so near-misses on a small dot still register. */
private const val POI_TAP_TOLERANCE_PX = 16f

private const val POI_SOURCE_ID = "pois"
private const val POI_LAYER_ID = "pois-points"
private const val CLUSTER_LAYER_SMALL = "pois-cluster-small"
private const val CLUSTER_LAYER_MEDIUM = "pois-cluster-medium"
private const val CLUSTER_LAYER_LARGE = "pois-cluster-large"
private const val CLUSTER_COUNT_LAYER_ID = "pois-cluster-count"
private const val LOCATION_SOURCE_ID = "location"
private const val LOCATION_ACCURACY_LAYER_ID = "location-accuracy"
private const val LOCATION_DOT_LAYER_ID = "location-dot"

/**
 * MapLibre map showing the filtered POIs as a clustered GeoJSON layer, one circle layer
 * per category color plus MapLibre's native clustering below [CLUSTER_MAX_ZOOM] so the
 * map stays readable when zoomed out. POIs come from Room; only the basemap tiles need
 * network.
 */
@Composable
fun PoiMap(
    pois: List<PoiWithDistance>,
    location: LatLon?,
    onPoiClick: (PoiWithDistance) -> Unit,
    customNames: Map<String, String> = emptyMap(),
    recenterRequest: Int = 0,
    focusTarget: LatLon? = null,
    focusRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Read once: the map/style is retained across Home<->Explore tab switches (ExploreMapHolder),
    // so a live system theme change while Explore is open won't re-style the map mid-session.
    val darkTheme = isSystemInDarkTheme()
    val markerBitmaps = rememberMarkerBitmaps(darkTheme)
    val markerBackground = markerBackgroundColor(darkTheme).toArgb()
    val markerForeground = markerForegroundColor(darkTheme).toArgb()

    val (mapView, state) = remember(context) { ExploreMapHolder.obtain(context) }
    state.onPoiClick = onPoiClick

    DisposableEffect(mapView) {
        if (!ExploreMapHolder.configured) {
            ExploreMapHolder.configured = true
            mapView.getMapAsync { map ->
                state.map = map
                map.cameraPosition = CameraPosition.Builder()
                    .target(
                        if (location != null) LatLng(location.lat, location.lon)
                        else LatLng(FALLBACK_LAT, FALLBACK_LON)
                    )
                    .zoom(DEFAULT_ZOOM)
                    .build()

                map.setStyle(
                    Style.Builder()
                        .fromUri(mapStyleUrl(darkTheme))
                        .withSource(
                            GeoJsonSource(POI_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray()), poiClusterOptions())
                        )
                        .withLayer(clusterCircleLayer(CLUSTER_LAYER_SMALL, markerBackground, markerForeground, upperBound = 20))
                        .withLayer(clusterCircleLayer(CLUSTER_LAYER_MEDIUM, markerBackground, markerForeground, lowerBound = 20, upperBound = 100))
                        .withLayer(clusterCircleLayer(CLUSTER_LAYER_LARGE, markerBackground, markerForeground, lowerBound = 100))
                        .withLayer(clusterCountLayer(markerForeground))
                        .withLayer(poiSymbolLayer())
                        .withSource(GeoJsonSource(LOCATION_SOURCE_ID))
                        .withLayer(locationAccuracyLayer(POSITION_DOT_COLOR))
                        .withLayer(locationDotLayer(POSITION_DOT_COLOR))
                ) { style ->
                    markerBitmaps.forEach { (category, bitmap) -> style.addImage(markerIconId(category), bitmap) }
                    state.style = style
                    state.poiSource = style.getSourceAs(POI_SOURCE_ID)
                    state.locationSource = style.getSourceAs(LOCATION_SOURCE_ID)
                    state.ready = true
                    applyPois(state, pois)
                    applyLocation(state, location)
                }

                map.addOnMapClickListener { latLng ->
                    handleMapClick(state, map, latLng)
                }
                map.addOnCameraIdleListener { applyLocation(state, state.renderedLocation) }
            }
        }
        onDispose { }
    }

    // Bumped by the "locate me" FAB; only recenter when it actually changes and a fix is available.
    var lastHandledRecenter by remember { mutableIntStateOf(0) }
    LaunchedEffect(recenterRequest, location) {
        if (recenterRequest != 0 && recenterRequest != lastHandledRecenter && location != null) {
            state.map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lon), LOCATE_ME_ZOOM)
            )
            lastHandledRecenter = recenterRequest
        }
    }

    // Bumped when a search suggestion is picked; jumps the camera to that POI regardless
    // of whether it's currently on-screen, since the whole point is to reveal it.
    var lastHandledFocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusRequest, focusTarget) {
        if (focusRequest != 0 && focusRequest != lastHandledFocus && focusTarget != null) {
            state.map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(focusTarget.lat, focusTarget.lon), SEARCH_FOCUS_ZOOM)
            )
            lastHandledFocus = focusRequest
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Deliberately not mapView.onDestroy(): the MapView is retained by ExploreMapHolder
            // across tab switches so its style/GL surface don't get rebuilt on every visit.
        }
    }

    AndroidView(
        factory = {
            // mapView is retained across recompositions of this screen; detach it from
            // whatever AndroidViewHolder last hosted it before this one adopts it.
            (mapView.parent as? ViewGroup)?.removeView(mapView)
            mapView
        },
        modifier = modifier.clipToBounds(),
        update = {
            if (state.renderedPois != pois) {
                applyPois(state, pois)
            }
            if (state.renderedLocation != location) {
                applyLocation(state, location)
            }
        }
    )
}

private class PoiMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var ready: Boolean = false
    var poiSource: GeoJsonSource? = null
    var locationSource: GeoJsonSource? = null
    var renderedPois: List<PoiWithDistance> = emptyList()
    var renderedLocation: LatLon? = null
    var poiById: Map<String, PoiWithDistance> = emptyMap()
    var onPoiClick: (PoiWithDistance) -> Unit = {}
}

/**
 * Retains the Explore [MapView] (and its loaded style/sources) across Home<->Explore tab
 * switches. Compose Navigation fully disposes and recomposes the Explore destination on every
 * switch, so without this the map would refetch its remote style and rebuild its GL surface on
 * every visit. Reset when the hosting Activity changes (e.g. a config change not handled
 * in-place), since a MapView can't outlive the Context it was created with.
 */
private object ExploreMapHolder {
    private var mapView: MapView? = null
    private var state: PoiMapState? = null
    private var owningContext: Context? = null

    /** Whether one-time setup (style/layers/listeners) has already run for the current [mapView]. */
    var configured: Boolean = false

    fun obtain(context: Context): Pair<MapView, PoiMapState> {
        val existingView = mapView
        if (existingView != null && owningContext === context) {
            return existingView to state!!
        }
        existingView?.onDestroy()
        val newView = MapView(context).apply { onCreate(null) }
        val newState = PoiMapState()
        mapView = newView
        state = newState
        owningContext = context
        configured = false
        return newView to newState
    }
}

private fun poiClusterOptions(): GeoJsonOptions =
    GeoJsonOptions().withCluster(true).withClusterMaxZoom(CLUSTER_MAX_ZOOM).withClusterRadius(CLUSTER_RADIUS)

private fun poiSymbolLayer(): SymbolLayer =
    SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID)
        .withProperties(
            PropertyFactory.iconImage(categoryIconExpression()),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
        .withFilter(Expression.not(Expression.has("point_count")))

private fun clusterCircleLayer(
    layerId: String,
    fillColor: Int,
    strokeColor: Int,
    lowerBound: Int? = null,
    upperBound: Int? = null
): CircleLayer {
    val pointCount = Expression.toNumber(Expression.get("point_count"))
    val filters = buildList {
        add(Expression.has("point_count"))
        lowerBound?.let { add(Expression.gte(pointCount, Expression.literal(it))) }
        upperBound?.let { add(Expression.lt(pointCount, Expression.literal(it))) }
    }
    val radius = when {
        upperBound == null -> 27f // largest tier (100+)
        lowerBound == null -> 18f // smallest tier (< 20)
        else -> 22f // middle tier (20-99)
    }
    return CircleLayer(layerId, POI_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleColor(fillColor),
            PropertyFactory.circleRadius(radius),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(strokeColor)
        )
        .withFilter(Expression.all(*filters.toTypedArray()))
}

private fun clusterCountLayer(textColor: Int): SymbolLayer =
    SymbolLayer(CLUSTER_COUNT_LAYER_ID, POI_SOURCE_ID)
        .withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textColor(textColor),
            PropertyFactory.textSize(13f),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true)
        )
        .withFilter(Expression.has("point_count"))

private fun locationAccuracyLayer(color: Int): CircleLayer =
    CircleLayer(LOCATION_ACCURACY_LAYER_ID, LOCATION_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleOpacity(0.2f),
            PropertyFactory.circleRadius(Expression.toNumber(Expression.get("accuracyRadius")))
        )
        .withFilter(Expression.has("accuracyRadius"))

private fun locationDotLayer(color: Int): CircleLayer =
    CircleLayer(LOCATION_DOT_LAYER_ID, LOCATION_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )

private fun applyPois(state: PoiMapState, pois: List<PoiWithDistance>) {
    state.renderedPois = pois
    state.poiById = pois.associateBy { it.poi.id }
    if (!state.ready) return
    val features = pois.map { p ->
        val props = JsonObject().apply {
            addProperty("id", p.poi.id)
            addProperty("category", p.poi.category)
        }
        Feature.fromGeometry(Point.fromLngLat(p.poi.lon, p.poi.lat), props)
    }
    state.poiSource?.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun applyLocation(state: PoiMapState, location: LatLon?) {
    state.renderedLocation = location
    if (!state.ready) return
    val source = state.locationSource ?: return
    if (location == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return
    }
    val accuracyMeters = location.accuracyMeters
    val zoom = state.map?.cameraPosition?.zoom ?: DEFAULT_ZOOM
    val props = JsonObject()
    if (accuracyMeters != null) {
        props.addProperty("accuracyRadius", metersToRadiusPx(accuracyMeters, location.lat, zoom))
    }
    val feature = Feature.fromGeometry(Point.fromLngLat(location.lon, location.lat), props)
    source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(feature)))
}

private fun metersToRadiusPx(meters: Float, latitude: Double, zoom: Double): Float {
    val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom)
    return (meters / metersPerPixel).toFloat()
}

private fun handleMapClick(state: PoiMapState, map: MapLibreMap, latLng: LatLng): Boolean {
    val screenPoint = map.projection.toScreenLocation(latLng)
    val clusterFeatures = map.queryRenderedFeatures(
        screenPoint, CLUSTER_LAYER_SMALL, CLUSTER_LAYER_MEDIUM, CLUSTER_LAYER_LARGE
    )
    val clusterPoint = clusterFeatures.firstOrNull()?.geometry() as? Point
    if (clusterPoint != null) {
        val zoom = (map.cameraPosition.zoom) + CLUSTER_TAP_ZOOM_STEP
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(clusterPoint.latitude(), clusterPoint.longitude()), zoom)
        )
        return true
    }
    val tapArea = RectF(
        screenPoint.x - POI_TAP_TOLERANCE_PX,
        screenPoint.y - POI_TAP_TOLERANCE_PX,
        screenPoint.x + POI_TAP_TOLERANCE_PX,
        screenPoint.y + POI_TAP_TOLERANCE_PX
    )
    val poiFeature = map.queryRenderedFeatures(tapArea, POI_LAYER_ID)
        .minByOrNull { feature ->
            val point = feature.geometry() as? Point
            if (point == null) {
                Float.MAX_VALUE
            } else {
                val featureScreen = map.projection.toScreenLocation(LatLng(point.latitude(), point.longitude()))
                hypot(featureScreen.x - screenPoint.x, featureScreen.y - screenPoint.y)
            }
        } ?: return false
    val id = poiFeature.getStringProperty("id") ?: return false
    state.poiById[id]?.let(state.onPoiClick)
    return true
}
