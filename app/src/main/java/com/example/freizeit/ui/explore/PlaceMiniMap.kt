package com.example.freizeit.ui.explore

import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.freizeit.data.entity.Poi
import com.example.freizeit.ui.common.categoryColor
import com.example.freizeit.util.LatLon
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val MINI_MAP_ZOOM = 15.0
private const val MINI_MAP_BORDER_PX = 90
private const val POI_DOT_SOURCE_ID = "mini-poi"
private const val POI_DOT_LAYER_ID = "mini-poi-layer"
private const val USER_DOT_SOURCE_ID = "mini-user"
private const val USER_DOT_LAYER_ID = "mini-user-layer"

/**
 * Small, mostly-static map for the place detail sheet: shows the place and, when
 * known, the user's current location, framed with enough border that neither
 * point sits on the map's edge.
 */
@Composable
fun PlaceMiniMap(
    poi: Poi,
    location: LatLon?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val poiColor = categoryColor(poi.category).toArgb()
    val locationColor = MaterialTheme.colorScheme.primary.toArgb()

    val state = remember { MiniMapState() }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            state.map = map
            map.setStyle(
                Style.Builder()
                    .withSource(cartoDarkMatterSource())
                    .withLayer(cartoDarkMatterLayer())
                    .withSource(GeoJsonSource(POI_DOT_SOURCE_ID))
                    .withLayer(dotLayer(POI_DOT_LAYER_ID, POI_DOT_SOURCE_ID, poiColor))
                    .withSource(GeoJsonSource(USER_DOT_SOURCE_ID))
                    .withLayer(dotLayer(USER_DOT_LAYER_ID, USER_DOT_SOURCE_ID, locationColor))
            ) { style ->
                state.style = style
                state.poiSource = style.getSourceAs(POI_DOT_SOURCE_ID)
                state.userSource = style.getSourceAs(USER_DOT_SOURCE_ID)
                state.ready = true
                render(state, mapView, poi, location)
            }
        }
        onDispose { }
    }

    LaunchedEffect(poi.id, location?.lat, location?.lon) {
        render(state, mapView, poi, location)
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

private class MiniMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var ready: Boolean = false
    var poiSource: GeoJsonSource? = null
    var userSource: GeoJsonSource? = null
}

private fun dotLayer(layerId: String, sourceId: String, color: Int): CircleLayer =
    CircleLayer(layerId, sourceId)
        .withProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(Color.WHITE)
        )

private fun render(state: MiniMapState, mapView: MapView, poi: Poi, location: LatLon?) {
    if (!state.ready) return
    state.poiSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(poi.lon, poi.lat)))

    if (location != null) {
        state.userSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(location.lon, location.lat)))
    } else {
        state.userSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    mapView.doOnLayout {
        val map = state.map ?: return@doOnLayout
        if (location != null) {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(poi.lat, poi.lon))
                .include(LatLng(location.lat, location.lon))
                .build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, MINI_MAP_BORDER_PX))
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(poi.lat, poi.lon), MINI_MAP_ZOOM))
        }
    }
}
