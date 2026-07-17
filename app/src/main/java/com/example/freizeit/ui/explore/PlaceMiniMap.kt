package com.example.freizeit.ui.explore

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.util.LatLon
import com.google.gson.JsonObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val MINI_MAP_ZOOM = 15.0
private const val MINI_MAP_BORDER_PX = 90
private const val POI_DOT_RADIUS = 9f
private const val POI_DOT_HIGHLIGHT_RADIUS = 14f
private const val POI_DOT_SOURCE_ID = "suggestions-poi"
private const val POI_DOT_LAYER_ID = "suggestions-poi-layer"
private const val USER_DOT_SOURCE_ID = "suggestions-user"
private const val USER_DOT_LAYER_ID = "suggestions-user-layer"

/**
 * Static, non-interactive overview map for the Home carousel: shows every suggestion POI
 * plus the user's current location, camera fit once to include all of them. Every POI dot is
 * colored by its category; the one whose id matches [selectedPoiId] renders larger. Tapping
 * any dot reports it via [onPoiClick] so the caller can keep the carousel in sync.
 */
@Composable
fun SuggestionsMiniMap(
    pois: List<Poi>,
    selectedPoiId: String?,
    location: LatLon?,
    onPoiClick: (Poi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val state = remember { SuggestionsMapState() }
    state.poiById = pois.associateBy { it.id }
    state.onPoiClick = onPoiClick

    val mapView = remember {
        // Default MapView renders to a GLSurfaceView, which composites straight through
        // SurfaceFlinger and ignores the Compose graphicsLayer alpha applied by the Home
        // swipe deck (translationX still works since that's just view positioning, but the
        // map wouldn't fade). Texture mode routes rendering through the normal View draw
        // pass instead, so it fades along with the rest of the card.
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .translucentTextureSurface(true)
        MapView(context, options).apply { onCreate(null) }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            state.map = map
            map.uiSettings.apply {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isDoubleTapGesturesEnabled = false
            }
            map.setStyle(
                Style.Builder()
                    .fromUri(DARK_MATTER_STYLE_URL)
                    .withSource(GeoJsonSource(POI_DOT_SOURCE_ID))
                    .withLayer(poiDotLayer())
                    .withSource(GeoJsonSource(USER_DOT_SOURCE_ID))
                    .withLayer(dotLayer(USER_DOT_LAYER_ID, USER_DOT_SOURCE_ID, POSITION_DOT_COLOR))
            ) { style ->
                state.style = style
                state.poiSource = style.getSourceAs(POI_DOT_SOURCE_ID)
                state.userSource = style.getSourceAs(USER_DOT_SOURCE_ID)
                state.ready = true
                renderSuggestions(state, mapView, pois, selectedPoiId, location)
            }
            map.addOnMapClickListener { latLng -> handleSuggestionsMapClick(state, map, latLng) }
        }
        onDispose { }
    }

    LaunchedEffect(pois, selectedPoiId, location?.lat, location?.lon) {
        renderSuggestions(state, mapView, pois, selectedPoiId, location)
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
        modifier = modifier.clipToBounds()
    )
}

private class SuggestionsMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var ready: Boolean = false
    var poiSource: GeoJsonSource? = null
    var userSource: GeoJsonSource? = null
    var poiById: Map<String, Poi> = emptyMap()
    var onPoiClick: (Poi) -> Unit = {}
}

private fun dotLayer(layerId: String, sourceId: String, color: Int): CircleLayer =
    CircleLayer(layerId, sourceId)
        .withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(POI_DOT_RADIUS),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )

private fun poiDotLayer(): CircleLayer {
    val selected = Expression.toBool(Expression.get("selected"))
    return CircleLayer(POI_DOT_LAYER_ID, POI_DOT_SOURCE_ID)
        .withProperties(
            PropertyFactory.circleColor(categoryColorExpression()),
            PropertyFactory.circleRadius(
                Expression.switchCase(
                    selected,
                    Expression.literal(POI_DOT_HIGHLIGHT_RADIUS),
                    Expression.literal(POI_DOT_RADIUS)
                )
            ),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )
}

private fun renderSuggestions(
    state: SuggestionsMapState,
    mapView: MapView,
    pois: List<Poi>,
    selectedPoiId: String?,
    location: LatLon?
) {
    if (!state.ready) return
    val features = pois.map { poi ->
        val props = JsonObject().apply {
            addProperty("id", poi.id)
            addProperty("category", poi.category)
            addProperty("selected", poi.id == selectedPoiId)
        }
        Feature.fromGeometry(Point.fromLngLat(poi.lon, poi.lat), props)
    }
    state.poiSource?.setGeoJson(FeatureCollection.fromFeatures(features))

    if (location != null) {
        state.userSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(location.lon, location.lat)))
    } else {
        state.userSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    if (pois.isEmpty()) return
    mapView.doOnLayout {
        val map = state.map ?: return@doOnLayout
        val points = pois.map { LatLng(it.lat, it.lon) } +
            listOfNotNull(location?.let { LatLng(it.lat, it.lon) })
        if (points.size <= 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), MINI_MAP_ZOOM))
        } else {
            val bounds = LatLngBounds.Builder().apply { points.forEach(::include) }.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, MINI_MAP_BORDER_PX))
        }
    }
}

private fun handleSuggestionsMapClick(state: SuggestionsMapState, map: MapLibreMap, latLng: LatLng): Boolean {
    val screenPoint = map.projection.toScreenLocation(latLng)
    val feature = map.queryRenderedFeatures(screenPoint, POI_DOT_LAYER_ID).firstOrNull() ?: return false
    val id = feature.getStringProperty("id") ?: return false
    state.poiById[id]?.let(state.onPoiClick)
    return true
}
