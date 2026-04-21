package com.example.freizeit.ui.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.freizeit.R
import com.example.freizeit.databinding.ActivityActivityListBinding
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory
import com.example.freizeit.ui.adapter.ActivityAdapter
import com.example.freizeit.ui.viewmodel.MainViewModel
import com.example.freizeit.util.MapViewHelper
import com.example.freizeit.util.distanceBetween
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.launch

class ActivityListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActivityListBinding
    private val viewModel: MainViewModel by viewModels()
    private var activityAdapter: ActivityAdapter? = null
    private var recommendationAdapter: ActivityAdapter? = null
    private lateinit var mapViewHelper: MapViewHelper

    private var category: ActivityCategory? = null
    private var isMapView = false
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private lateinit var prefs: SharedPreferences

    private var cachedActivities: List<Activity> = emptyList()

    private var savedMapCenter: GeoPoint? = null
    private var savedMapZoom: Double = 17.0

    companion object {
        private const val TAG = "ActivityListActivity"
        private const val PREFS_NAME = "freizeit_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().userAgentValue = packageName
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val categoryName = intent.getStringExtra("category")
        category = categoryName?.let { ActivityCategory.valueOf(it) }

        userLatitude = intent.getDoubleExtra("latitude", 0.0)
        userLongitude = intent.getDoubleExtra("longitude", 0.0)

        Log.d(TAG, "Category: ${category?.name ?: "ALL"}, Location: $userLatitude, $userLongitude")

        setupToolbar()
        setupRecyclerViews()
        setupFab()
        setupRecommendationClose()
        setupMapViewHelper()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportActionBar?.title = if (category != null) {
            getCategoryName(category!!)
        } else {
            getString(R.string.category_all)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        binding.recommendationRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ActivityListActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.activitiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ActivityListActivity)
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            openActivityEdit(null)
        }
    }

    private fun setupRecommendationClose() {
        binding.closeRecommendation.setOnClickListener {
            binding.recommendationCard.visibility = View.GONE
            binding.spacer.visibility = View.GONE
            val categoryKey = category?.name ?: "all"
            prefs.edit().putBoolean("recommendation_closed_$categoryKey", true).apply()
        }
    }

    private fun setupMapViewHelper() {
        mapViewHelper = MapViewHelper(
            context = this,
            mapView = binding.mapView,
            detailCard = binding.mapDetailCard,
            detailCategoryIcon = binding.mapDetailCategoryIcon,
            detailActivityName = binding.mapDetailActivityName,
            detailActivityDetails = binding.mapDetailActivityDetails,
            detailMapsIcon = binding.mapDetailMapsIcon,
            detailClose = binding.mapDetailClose
        )

        mapViewHelper.setupMapView()

        mapViewHelper.setOnActivityEditClick { activity ->
            openActivityEdit(activity)
        }
    }

    private fun observeViewModel() {
        if (category != null) {
            viewModel.getActivitiesByCategory(category!!).observe(this) { activities ->
                cachedActivities = activities
                updateActivities()
            }
        } else {
            viewModel.allActivities.observe(this) { activities ->
                cachedActivities = activities
                updateActivities()
            }
        }
    }

    private fun updateActivities() {
        val allActivities = cachedActivities

        if (allActivities.isEmpty()) {
            binding.recommendationCard.visibility = View.GONE
            binding.spacer.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.listView.visibility = View.GONE
            return
        }

        val nearbyActivities = allActivities.filter { activity ->
            activity.getDistanceTo(userLatitude, userLongitude) < 100.0
        }

        if (nearbyActivities.isEmpty()) {
            binding.recommendationCard.visibility = View.GONE
            binding.spacer.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.listView.visibility = View.GONE
            return
        }

        val categoryKey = category?.name ?: "all"
        val recommendationClosed = prefs.getBoolean("recommendation_closed_$categoryKey", false)

        if (!recommendationClosed) {
            val recommendation = getDailyRecommendation(nearbyActivities, categoryKey)

            if (recommendation != null) {
                binding.recommendationCard.visibility = View.VISIBLE
                binding.spacer.visibility = View.VISIBLE
                if (recommendationAdapter == null) {
                    recommendationAdapter = ActivityAdapter(userLatitude, userLongitude) { activity ->
                        openActivityEdit(activity)
                    }
                    binding.recommendationRecyclerView.adapter = recommendationAdapter
                }
                recommendationAdapter?.submitList(listOf(recommendation))
            }
        } else {
            binding.recommendationCard.visibility = View.GONE
            binding.spacer.visibility = View.GONE
        }

        val sortedActivities = nearbyActivities.sortedBy {
            it.getDistanceTo(userLatitude, userLongitude)
        }

        if (activityAdapter == null) {
            activityAdapter = ActivityAdapter(userLatitude, userLongitude) { activity ->
                openActivityEdit(activity)
            }
            binding.activitiesRecyclerView.adapter = activityAdapter
        }

        activityAdapter?.submitList(sortedActivities)

        binding.emptyState.visibility = View.GONE
        binding.listView.visibility = View.VISIBLE
    }

    private fun openActivityEdit(activity: Activity?) {
        val intent = Intent(this, ActivityEditActivity::class.java)
        if (activity != null) {
            intent.putExtra("activity_id", activity.id)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_list, menu)

        val mapViewItem = menu.findItem(R.id.action_toggle_view)
        mapViewItem.setIcon(
            if (isMapView) R.drawable.ic_list_view
            else R.drawable.ic_map_view
        )

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_view -> {
                isMapView = !isMapView
                if (isMapView) showMapView() else showListView()
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showMapView() {
        binding.listView.visibility = View.GONE
        binding.mapView.visibility = View.VISIBLE
        binding.fab.visibility = View.GONE

        if (savedMapCenter != null) {
            mapViewHelper.setMapCenterAndZoom(savedMapCenter, savedMapZoom)
        }

        mapViewHelper.setUserLocation(userLatitude, userLongitude)

        val nearbyActivities = cachedActivities.filter { activity ->
            activity.getDistanceTo(userLatitude, userLongitude) < 100.0
        }

        val centerOnUser = savedMapCenter == null
        mapViewHelper.showActivitiesOnMap(nearbyActivities, centerOnUser = centerOnUser)
    }

    private fun showListView() {
        savedMapCenter = mapViewHelper.getMapCenter()
        savedMapZoom = mapViewHelper.getMapZoom()

        mapViewHelper.hideDetailCard()

        binding.mapView.visibility = View.GONE
        binding.listView.visibility = View.VISIBLE
        binding.fab.visibility = View.VISIBLE
    }

    private fun getCategoryName(category: ActivityCategory): String {
        return when (category) {
            ActivityCategory.WALK -> getString(R.string.category_walk)
            ActivityCategory.CAFE -> getString(R.string.category_cafe)
            ActivityCategory.RESTAURANT -> getString(R.string.category_restaurant)
            ActivityCategory.PLAYGROUND -> getString(R.string.category_playground)
            ActivityCategory.PARK -> getString(R.string.category_park)
            ActivityCategory.JOGGING -> getString(R.string.category_jogging)
            ActivityCategory.CYCLING -> getString(R.string.category_cycling)
            ActivityCategory.ICE_CREAM -> getString(R.string.category_ice_cream)
        }
    }

    private fun getDailyRecommendation(activities: List<Activity>, categoryKey: String): Activity? {
        if (activities.isEmpty()) return null

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val savedDate = prefs.getString("recommendation_date_$categoryKey", "")
        val savedActivityId = prefs.getLong("recommendation_id_$categoryKey", -1L)
        val savedLat = prefs.getFloat("recommendation_lat_$categoryKey", 0f).toDouble()
        val savedLon = prefs.getFloat("recommendation_lon_$categoryKey", 0f).toDouble()

        val distanceToSaved = if (savedLat != 0.0 && savedLon != 0.0) {
            distanceBetween(userLatitude, userLongitude, savedLat, savedLon)
        } else {
            Double.MAX_VALUE
        }

        val needsNewRecommendation = savedDate != today ||
                distanceToSaved > 50.0 ||
                activities.none { it.id == savedActivityId }

        return if (needsNewRecommendation) {
            val newRecommendation = activities.random()
            prefs.edit()
                .putString("recommendation_date_$categoryKey", today)
                .putLong("recommendation_id_$categoryKey", newRecommendation.id)
                .putFloat("recommendation_lat_$categoryKey", userLatitude.toFloat())
                .putFloat("recommendation_lon_$categoryKey", userLongitude.toFloat())
                .apply()
            newRecommendation
        } else {
            activities.find { it.id == savedActivityId } ?: activities.random()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
