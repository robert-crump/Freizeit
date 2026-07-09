package com.example.freizeit.ui.explore

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
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

private const val MINI_MAP_ZOOM = 15.0
private const val MINI_MAP_BORDER_PX = 90

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
    val density = context.resources.displayMetrics.density

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(CARTO_DARK_MATTER)
            setMultiTouchControls(true)
            overlays.add(CopyrightOverlay(context))
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

    LaunchedEffect(poi.id, location?.lat, location?.lon) {
        val poiPoint = GeoPoint(poi.lat, poi.lon)
        mapView.overlays.removeAll { it is DotOverlay }
        mapView.overlays.add(DotOverlay(poiPoint, poiColor, density = density))

        val userPoint = location?.let { GeoPoint(it.lat, it.lon) }
        if (userPoint != null) {
            mapView.overlays.add(DotOverlay(userPoint, locationColor, location.accuracyMeters, density))
        }
        mapView.invalidate()

        mapView.doOnLayout {
            if (userPoint != null) {
                val bbox = BoundingBox.fromGeoPoints(listOf(poiPoint, userPoint))
                mapView.zoomToBoundingBox(bbox, false, MINI_MAP_BORDER_PX)
            } else {
                mapView.controller.setZoom(MINI_MAP_ZOOM)
                mapView.controller.setCenter(poiPoint)
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clipToBounds()
    )
}
