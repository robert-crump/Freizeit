package com.example.freizeit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.freizeit.R
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory
import com.google.android.material.card.MaterialCardView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer

class MapViewHelper(
    private val context: Context,
    private val mapView: MapView,
    private val detailCard: MaterialCardView,
    private val detailCategoryIcon: ImageView,
    private val detailActivityName: TextView,
    private val detailActivityDetails: TextView,
    private val detailMapsIcon: ImageView,
    private val detailClose: ImageView
) {
    private var currentDetailActivity: Activity? = null
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var onActivityEditClick: ((Activity) -> Unit)? = null

    fun setupMapView() {
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        detailClose.setOnClickListener {
            hideDetailCard()
        }
    }

    fun setUserLocation(latitude: Double, longitude: Double) {
        userLatitude = latitude
        userLongitude = longitude
    }

    fun setOnActivityEditClick(callback: (Activity) -> Unit) {
        onActivityEditClick = callback
    }

    fun showActivitiesOnMap(
        activities: List<Activity>,
        centerOnUser: Boolean = true,
        zoomLevel: Double = 17.0
    ) {
        mapView.overlays.clear()

        if (userLatitude != 0.0 && userLongitude != 0.0) {
            val userMarker = Marker(mapView)
            userMarker.position = GeoPoint(userLatitude, userLongitude)
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            userMarker.title = context.getString(R.string.marker_your_location)

            userMarker.icon = context.resources.getDrawable(R.drawable.ic_location, null)?.apply {
                setTint(context.resources.getColor(R.color.turquoise_primary, null))
                setBounds(0, 0, intrinsicWidth * 2, intrinsicHeight * 2)
            }

            mapView.overlays.add(userMarker)

            if (centerOnUser) {
                mapView.controller.setCenter(GeoPoint(userLatitude, userLongitude))
                mapView.controller.setZoom(zoomLevel)
            }
        }

        val clusterMarkers = RadiusMarkerClusterer(context)

        activities.forEach { activity ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(activity.latitude, activity.longitude)
            marker.title = activity.name
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = null

            val dist = activity.getDistanceTo(userLatitude, userLongitude)
            marker.snippet = if (dist < 1.0) {
                val roundedMeters = ((dist * 1000).toInt() / 10) * 10
                "$roundedMeters m"
            } else {
                String.format("%.1f km", dist)
            }

            marker.setOnMarkerClickListener { _, _ ->
                showDetailCard(activity)
                true
            }
            clusterMarkers.add(marker)
        }

        mapView.overlays.add(clusterMarkers)
        mapView.invalidate()
    }

    fun hideDetailCard() {
        detailCard.visibility = View.GONE
        currentDetailActivity = null
    }

    private fun showDetailCard(activity: Activity) {
        if (currentDetailActivity != null && currentDetailActivity?.id != activity.id) {
            hideDetailCard()
        }

        currentDetailActivity = activity

        detailCategoryIcon.setImageResource(getCategoryIcon(activity.category))
        detailActivityName.text = activity.name

        val distance = activity.getDistanceTo(userLatitude, userLongitude)
        val distanceText = if (distance < 1.0) {
            val roundedMeters = ((distance * 1000).toInt() / 10) * 10
            "$roundedMeters m"
        } else {
            String.format("%.1f km", distance)
        }

        val categoryName = getCategoryName(activity.category)
        val typeText = if (activity.isIndoor) {
            context.getString(R.string.activity_indoor)
        } else {
            context.getString(R.string.activity_outdoor)
        }
        detailActivityDetails.text = "$categoryName | $typeText | $distanceText"

        detailMapsIcon.setOnClickListener {
            openInGoogleMaps(activity)
        }

        detailCard.setOnClickListener {
            onActivityEditClick?.invoke(activity)
        }

        detailCard.visibility = View.VISIBLE

        val point = GeoPoint(activity.latitude, activity.longitude)
        mapView.controller.animateTo(point)
    }

    private fun openInGoogleMaps(activity: Activity) {
        val uri = if (!activity.address.isNullOrEmpty()) {
            val query = Uri.encode("${activity.name}, ${activity.address}")
            "geo:0,0?q=$query"
        } else {
            "geo:${activity.latitude},${activity.longitude}?q=${activity.latitude},${activity.longitude}(${Uri.encode(activity.name)})"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.setPackage("com.google.android.apps.maps")

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val browserUri = if (!activity.address.isNullOrEmpty()) {
                val query = Uri.encode("${activity.name}, ${activity.address}")
                "https://www.google.com/maps/search/?api=1&query=$query"
            } else {
                "https://www.google.com/maps/search/?api=1&query=${activity.latitude},${activity.longitude}"
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUri))
            context.startActivity(browserIntent)
        }
    }

    private fun getCategoryName(category: ActivityCategory): String {
        return when (category) {
            ActivityCategory.WALK -> context.getString(R.string.category_walk)
            ActivityCategory.CAFE -> context.getString(R.string.category_cafe)
            ActivityCategory.RESTAURANT -> context.getString(R.string.category_restaurant)
            ActivityCategory.PLAYGROUND -> context.getString(R.string.category_playground)
            ActivityCategory.PARK -> context.getString(R.string.category_park)
            ActivityCategory.JOGGING -> context.getString(R.string.category_jogging)
            ActivityCategory.CYCLING -> context.getString(R.string.category_cycling)
            ActivityCategory.ICE_CREAM -> context.getString(R.string.category_ice_cream)
        }
    }

    private fun getCategoryIcon(category: ActivityCategory): Int {
        return when (category) {
            ActivityCategory.WALK -> R.drawable.ic_walk_category
            ActivityCategory.CAFE -> R.drawable.ic_cafe
            ActivityCategory.RESTAURANT -> R.drawable.ic_restaurant
            ActivityCategory.PLAYGROUND -> R.drawable.ic_playground
            ActivityCategory.PARK -> R.drawable.ic_park
            ActivityCategory.JOGGING -> R.drawable.ic_jogging
            ActivityCategory.CYCLING -> R.drawable.ic_cycling
            ActivityCategory.ICE_CREAM -> R.drawable.ic_star
        }
    }

    fun getMapCenter(): GeoPoint? {
        return mapView.mapCenter as? GeoPoint
    }

    fun getMapZoom(): Double {
        return mapView.zoomLevelDouble
    }

    fun setMapCenterAndZoom(center: GeoPoint?, zoom: Double) {
        center?.let {
            mapView.controller.setCenter(it)
            mapView.controller.setZoom(zoom)
        }
    }
}
