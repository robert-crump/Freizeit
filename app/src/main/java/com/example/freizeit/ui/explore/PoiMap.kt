package com.example.freizeit.ui.explore

import android.graphics.Color
import android.graphics.RectF
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

private const val FALLBACK_LAT = 50.94 // Cologne area, center of the extract coverage
private const val FALLBACK_LON = 6.96
private const val DEFAULT_ZOOM = 12.0
private const val LOCATE_ME_ZOOM = 16.0

/** Below this zoom, MapLibre's built-in GeoJSON clustering groups POIs into size-tiered bubbles. */
private const val CLUSTER_MAX_ZOOM = 11
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clusterColor = MaterialTheme.colorScheme.secondary.toArgb()

    val state = remember { PoiMapState() }
    state.onPoiClick = onPoiClick

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(mapView) {
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
                    .fromUri(DARK_MATTER_STYLE_URL)
                    .withSource(
                        GeoJsonSource(POI_SOURCE_ID, FeatureCollection.fromFeatures(emptyArray()), poiClusterOptions())
                    )
                    .withLayer(clusterCircleLayer(CLUSTER_LAYER_SMALL, clusterColor, upperBound = 20))
                    .withLayer(clusterCircleLayer(CLUSTER_LAYER_MEDIUM, clusterColor, lowerBound = 20, upperBound = 100))
                    .withLayer(clusterCircleLayer(CLUSTER_LAYER_LARGE, clusterColor, lowerBound = 100))
                    .withLayer(clusterCountLayer())
                    .withLayer(poiCircleLayer())
                    .withSource(GeoJsonSource(LOCATION_SOURCE_ID))
                    .withLayer(locationAccuracyLayer(POSITION_DOT_COLOR))
                    .withLayer(locationDotLayer(POSITION_DOT_COLOR))
            ) { style ->
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
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
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

private fun poiClusterOptions(): GeoJsonOptions =
    GeoJsonOptions().withCluster(true).withClusterMaxZoom(CLUSTER_MAX_ZOOM).withClusterRadius(CLUSTER_RADIUS)

private fun poiCircleLayer(): CircleLayer =
    CircleLayer(POI_LAYER_ID, POI_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleColor(categoryColorExpression()),
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
        .withFilter(Expression.not(Expression.has("point_count")))

private fun clusterCircleLayer(
    layerId: String,
    color: Int,
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
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(radius),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
        .withFilter(Expression.all(*filters.toTypedArray()))
}

private fun clusterCountLayer(): SymbolLayer =
    SymbolLayer(CLUSTER_COUNT_LAYER_ID, POI_SOURCE_ID)
        .withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textColor(Color.WHITE),
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
