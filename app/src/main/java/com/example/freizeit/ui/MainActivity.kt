package com.example.freizeit.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.freizeit.R
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory
import com.example.freizeit.databinding.ActivityMainBinding
import com.example.freizeit.ui.activity.ActivityEditActivity
import com.example.freizeit.ui.activity.ActivityListActivity
import com.example.freizeit.ui.activity.RandomActivityActivity
import com.example.freizeit.ui.adapter.ActivityAdapter
import com.example.freizeit.ui.settings.SettingsActivity
import com.example.freizeit.ui.viewmodel.MainViewModel
import com.example.freizeit.util.FilterOptions
import com.example.freizeit.util.IndoorOutdoorFilter
import com.example.freizeit.util.MapViewHelper
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var activityAdapter: ActivityAdapter? = null
    private var recommendationAdapter: ActivityAdapter? = null
    private lateinit var mapViewHelper: MapViewHelper

    private var isMapView = false
    private var cachedActivities: List<Activity> = emptyList()

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "freizeit_prefs"
        private const val KEY_SAMPLE_DATA_INSERTED = "sample_data_inserted"
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Location permission granted")
            viewModel.updateCurrentLocation()
        } else {
            Log.w(TAG, "Location permission denied")
            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().userAgentValue = packageName

        setupToolbar()
        setupDrawer()
        setupRecommendation()
        setupRecyclerView()
        setupFilterChips()
        setupViewModeToggle()
        setupMapView()
        setupFab()
        observeViewModel()
        requestLocationPermission()

        checkAndInsertSampleData()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun checkAndInsertSampleData() {
        lifecycleScope.launch {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val hasInsertedData = prefs.getBoolean(KEY_SAMPLE_DATA_INSERTED, false)

            if (!hasInsertedData) {
                insertSampleData()
                prefs.edit().putBoolean(KEY_SAMPLE_DATA_INSERTED, true).apply()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
    }

    private fun setupDrawer() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_all -> openCategory(null)
                R.id.nav_walk -> openCategory(ActivityCategory.WALK)
                R.id.nav_cafe -> openCategory(ActivityCategory.CAFE)
                R.id.nav_restaurant -> openCategory(ActivityCategory.RESTAURANT)
                R.id.nav_playground -> openCategory(ActivityCategory.PLAYGROUND)
                R.id.nav_park -> openCategory(ActivityCategory.PARK)
                R.id.nav_jogging -> openCategory(ActivityCategory.JOGGING)
                R.id.nav_cycling -> openCategory(ActivityCategory.CYCLING)
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun openCategory(category: ActivityCategory?) {
        val location = viewModel.currentLocation.value

        if (location != null) {
            val intent = Intent(this, ActivityListActivity::class.java)
            intent.putExtra("category", category?.name)
            intent.putExtra("latitude", location.first)
            intent.putExtra("longitude", location.second)
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.location_pending, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecommendation() {
        binding.closeRecommendation.setOnClickListener {
            binding.recommendationCard.visibility = View.GONE
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putBoolean("recommendation_closed_main", true).apply()
        }

        binding.recommendationRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupRecyclerView() {
        binding.activitiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupViewModeToggle() {
        binding.viewModeToggle.check(R.id.listViewButton)

        binding.listViewButton.setOnClickListener { showListView() }
        binding.mapViewButton.setOnClickListener { showMapView() }
    }

    private fun setupMapView() {
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
            val intent = Intent(this, ActivityEditActivity::class.java)
            intent.putExtra("activity_id", activity.id)
            startActivity(intent)
        }
    }

    private fun showListView() {
        isMapView = false
        binding.listViewContainer.visibility = View.VISIBLE
        binding.mapViewContainer.visibility = View.GONE
        binding.fab.visibility = View.VISIBLE
        mapViewHelper.hideDetailCard()
    }

    private fun showMapView() {
        isMapView = true
        binding.listViewContainer.visibility = View.GONE
        binding.mapViewContainer.visibility = View.VISIBLE
        binding.fab.visibility = View.GONE

        val location = viewModel.currentLocation.value
        if (location != null && cachedActivities.isNotEmpty()) {
            mapViewHelper.setUserLocation(location.first, location.second)
            mapViewHelper.showActivitiesOnMap(cachedActivities)
        }
    }

    private fun setupFilterChips() {
        binding.filterIndoorChip.setOnCheckedChangeListener { _, _ -> updateFilterFromChips() }
        binding.filterOutdoorChip.setOnCheckedChangeListener { _, _ -> updateFilterFromChips() }
    }

    private fun updateFilterFromChips() {
        val indoorChecked = binding.filterIndoorChip.isChecked
        val outdoorChecked = binding.filterOutdoorChip.isChecked

        val filter = when {
            indoorChecked && outdoorChecked -> IndoorOutdoorFilter.BOTH
            indoorChecked -> IndoorOutdoorFilter.INDOOR
            outdoorChecked -> IndoorOutdoorFilter.OUTDOOR
            else -> IndoorOutdoorFilter.BOTH
        }

        if (!indoorChecked && !outdoorChecked) {
            binding.filterIndoorChip.isChecked = true
            binding.filterOutdoorChip.isChecked = true
            return
        }

        viewModel.updateFilterOptions(
            FilterOptions(
                maxDistance = null,
                indoorOutdoor = filter
            )
        )
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            startActivity(Intent(this, ActivityEditActivity::class.java))
        }
    }

    private fun observeViewModel() {
        binding.locationProgress.visibility = View.VISIBLE

        viewModel.currentLocationName.observe(this) { locationName ->
            binding.locationText.text = locationName
            if (locationName != getString(R.string.location_loading)) {
                binding.locationProgress.visibility = View.GONE
            }
        }

        viewModel.currentLocation.observe(this) { location ->
            binding.locationProgress.visibility = View.GONE

            activityAdapter = ActivityAdapter(location.first, location.second) { activity ->
                val intent = Intent(this, ActivityEditActivity::class.java)
                intent.putExtra("activity_id", activity.id)
                startActivity(intent)
            }
            binding.activitiesRecyclerView.adapter = activityAdapter

            updateActivitiesList()

            val selectedFavorite = viewModel.selectedFavoriteLocation.value
            if (selectedFavorite == null) {
                val favoriteLocations = viewModel.allFavoriteLocations.value ?: emptyList()
                val nearbyFavorite = favoriteLocations.firstOrNull { fav ->
                    fav.getDistanceTo(location.first, location.second) < 0.3
                }
                if (nearbyFavorite != null) {
                    viewModel.selectFavoriteLocation(nearbyFavorite)
                }
            }
        }

        viewModel.allActivities.observe(this) { updateActivitiesList() }

        viewModel.filterOptions.observe(this) { updateActivitiesList() }

        viewModel.allFavoriteLocations.observe(this) { }
    }

    private fun updateActivitiesList() {
        val location = viewModel.currentLocation.value
        val allActivities = viewModel.allActivities.value ?: emptyList()
        val filterOptions = viewModel.filterOptions.value ?: FilterOptions()

        if (location == null || activityAdapter == null) {
            binding.emptyState.visibility = View.VISIBLE
            binding.activitiesRecyclerView.visibility = View.GONE
            binding.recommendationCard.visibility = View.GONE
            binding.viewModeToggle.visibility = View.GONE
            binding.emptyStateText.text = getString(R.string.location_loading)
            return
        }

        if (allActivities.isNotEmpty()) {
            var filteredActivities = allActivities.filter { activity ->
                activity.getDistanceTo(location.first, location.second) < 100.0
            }

            filteredActivities = when (filterOptions.indoorOutdoor) {
                IndoorOutdoorFilter.INDOOR -> filteredActivities.filter { it.isIndoor }
                IndoorOutdoorFilter.OUTDOOR -> filteredActivities.filter { !it.isIndoor }
                IndoorOutdoorFilter.BOTH -> filteredActivities
            }

            val sortedActivities = filteredActivities.sortedBy {
                it.getDistanceTo(location.first, location.second)
            }

            cachedActivities = sortedActivities
            binding.viewModeToggle.visibility = View.VISIBLE

            activityAdapter?.submitList(sortedActivities)
            showRecommendation(sortedActivities, location.first, location.second)

            if (sortedActivities.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.activitiesRecyclerView.visibility = View.GONE
                binding.emptyStateText.text = getString(R.string.activity_no_nearby)
            } else {
                binding.emptyState.visibility = View.GONE
                binding.activitiesRecyclerView.visibility = View.VISIBLE
            }

            if (isMapView) {
                mapViewHelper.setUserLocation(location.first, location.second)
                mapViewHelper.showActivitiesOnMap(sortedActivities, centerOnUser = false)
            }
        } else {
            binding.emptyState.visibility = View.VISIBLE
            binding.activitiesRecyclerView.visibility = View.GONE
            binding.recommendationCard.visibility = View.GONE
            binding.viewModeToggle.visibility = View.GONE
            binding.emptyStateText.text = getString(R.string.no_activities)
        }
    }

    private fun showRecommendation(activities: List<Activity>, userLat: Double, userLon: Double) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val recommendationClosed = prefs.getBoolean("recommendation_closed_main", false)

        if (recommendationClosed || activities.isEmpty()) {
            binding.recommendationCard.visibility = View.GONE
            return
        }

        val recommendation = activities.randomOrNull()

        if (recommendation != null) {
            if (recommendationAdapter == null) {
                recommendationAdapter = ActivityAdapter(userLat, userLon) { activity ->
                    val intent = Intent(this, ActivityEditActivity::class.java)
                    intent.putExtra("activity_id", activity.id)
                    startActivity(intent)
                }
                binding.recommendationRecyclerView.adapter = recommendationAdapter
            }
            recommendationAdapter?.submitList(listOf(recommendation))
            binding.recommendationCard.visibility = View.VISIBLE
        }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_random -> {
                val location = viewModel.currentLocation.value
                if (location != null) {
                    val intent = Intent(this, RandomActivityActivity::class.java)
                    intent.putExtra("latitude", location.first)
                    intent.putExtra("longitude", location.second)
                    startActivity(intent)
                }
                true
            }
            R.id.action_refresh_location -> {
                binding.locationText.text = getString(R.string.location_loading)
                binding.locationProgress.visibility = View.VISIBLE
                viewModel.updateCurrentLocation()
                Toast.makeText(this, R.string.location_updating, Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    private fun insertSampleData() {
        lifecycleScope.launch {
            val sampleActivities = listOf(
                Activity(
                    name = "Auberge A Gen Kirk",
                    category = ActivityCategory.CAFE,
                    latitude = 50.78742,
                    longitude = 5.96359,
                    isIndoor = true,
                    address = "Vijlenberg 115, 6294 AS Vijlen"
                ),
                Activity(
                    name = "Kaffee Erhard",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7726,
                    longitude = 6.0912,
                    isIndoor = true,
                    address = "Jakobstraße 96, 52064 Aachen"
                ),
                Activity(
                    name = "Monsieur Daniel",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7743,
                    longitude = 6.0867,
                    isIndoor = true,
                    address = "Kleinmarschierstraße 7-9, 52062 Aachen"
                ),
                Activity(
                    name = "Cafe & Galerie de Gau",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7720,
                    longitude = 6.0175,
                    isIndoor = true,
                    address = "Von Clermontplein 32, 6291 AV Vaals"
                ),
                Activity(
                    name = "Boscafé 't Hijgend Hert",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7862,
                    longitude = 5.9713,
                    isIndoor = false,
                    address = "Harles 23, 6294 NG Vijlen"
                ),
                Activity(
                    name = "Fixed Gear Coffee Valkenburg",
                    category = ActivityCategory.CAFE,
                    latitude = 50.8647,
                    longitude = 5.8263,
                    isIndoor = true,
                    address = "Daalhemerweg 4, 6301 BK Valkenburg"
                ),
                Activity(
                    name = "Fixed Gear Coffee Maastricht",
                    category = ActivityCategory.CAFE,
                    latitude = 50.8493,
                    longitude = 5.6918,
                    isIndoor = true,
                    address = "Grote Gracht 42, 6211 SX Maastricht"
                ),
                Activity(
                    name = "Baristinho",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7741,
                    longitude = 6.0855,
                    isIndoor = true,
                    address = "Kleinmarschierstraße 50-52, 52062 Aachen"
                ),
                Activity(
                    name = "Café Juli",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7747,
                    longitude = 6.0891,
                    isIndoor = true,
                    address = "Sandkaulstraße 15, 52062 Aachen"
                ),
                Activity(
                    name = "Katapult",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7779,
                    longitude = 6.0756,
                    isIndoor = true,
                    address = "Templergraben 57, 52062 Aachen"
                ),
                Activity(
                    name = "Lammerskötter Café",
                    category = ActivityCategory.CAFE,
                    latitude = 50.7612,
                    longitude = 6.1023,
                    isIndoor = true,
                    address = "Altdorfstraße 3-5, 52066 Aachen"
                ),
                Activity(
                    name = "Hollandwiese",
                    category = ActivityCategory.PARK,
                    latitude = 50.7790,
                    longitude = 6.0610,
                    isIndoor = false,
                    address = "Hollandwiese, 52062 Aachen"
                ),
                Activity(
                    name = "Frankenberger Park",
                    category = ActivityCategory.PARK,
                    latitude = 50.7700,
                    longitude = 6.0920,
                    isIndoor = false,
                    address = "Frankenberger Park, 52066 Aachen"
                ),
                Activity(
                    name = "Westpark",
                    category = ActivityCategory.PARK,
                    latitude = 50.7830,
                    longitude = 6.0680,
                    isIndoor = false,
                    address = "Westpark, 52062 Aachen"
                ),
                Activity(
                    name = "Von-Halfern-Park",
                    category = ActivityCategory.PARK,
                    latitude = 50.7820,
                    longitude = 6.0950,
                    isIndoor = false,
                    address = "Von-Halfern-Park, 52066 Aachen"
                ),
                Activity(
                    name = "Brunssummerheide",
                    category = ActivityCategory.PARK,
                    latitude = 50.9283,
                    longitude = 5.9783,
                    isIndoor = false,
                    address = "Brunssummerheide, 6441 Heerlen"
                ),
                Activity(
                    name = "IKEA Restaurant Heerlen",
                    category = ActivityCategory.RESTAURANT,
                    latitude = 50.9070,
                    longitude = 5.9850,
                    isIndoor = true,
                    address = "In de Cramer 142, 6412 PM Heerlen"
                ),
                Activity(
                    name = "Feinkost aus Italien – Da Massimo",
                    category = ActivityCategory.RESTAURANT,
                    latitude = 50.7743,
                    longitude = 6.0867,
                    isIndoor = true,
                    address = "Aachen"
                ),
                Activity(
                    name = "Eyserhalte",
                    category = ActivityCategory.RESTAURANT,
                    latitude = 50.8378,
                    longitude = 5.9205,
                    isIndoor = true,
                    address = "Wittemerweg 2, 6287 AB Eys"
                ),
                Activity(
                    name = "Gerardushoeve",
                    category = ActivityCategory.RESTAURANT,
                    latitude = 50.8097,
                    longitude = 5.9788,
                    isIndoor = false,
                    address = "Julianastraat 23, 6285 AH Epen"
                ),
                Activity(
                    name = "Spielplatz Veltmanplatz",
                    category = ActivityCategory.PLAYGROUND,
                    latitude = 50.7763,
                    longitude = 6.0841,
                    isIndoor = false,
                    address = "Veltmanplatz 8, 52062 Aachen"
                ),
                Activity(
                    name = "Spielplatz Rütscher Straße",
                    category = ActivityCategory.PLAYGROUND,
                    latitude = 50.7913,
                    longitude = 6.0732,
                    isIndoor = false,
                    address = "Rütscher Straße, 52072 Aachen"
                ),
                Activity(
                    name = "Spielplatz Kurpark Monheimsallee",
                    category = ActivityCategory.PLAYGROUND,
                    latitude = 50.7634,
                    longitude = 6.0934,
                    isIndoor = false,
                    address = "Monheimsallee, 52062 Aachen"
                ),
                Activity(
                    name = "Spielplatz Lindenplatz",
                    category = ActivityCategory.PLAYGROUND,
                    latitude = 50.7750,
                    longitude = 6.0810,
                    isIndoor = false,
                    address = "Lindenplatz, 52062 Aachen"
                ),
                Activity(
                    name = "Hoogst gelegen speeltuin van Nederland",
                    category = ActivityCategory.PLAYGROUND,
                    latitude = 50.7720,
                    longitude = 6.0200,
                    isIndoor = false,
                    address = "Vaals, Netherlands"
                ),
                Activity(
                    name = "Spielplatz Alter Tivoli",
                    category = ActivityCategory.PLAYGROUND,
                    latitude = 50.7760,
                    longitude = 6.0900,
                    isIndoor = false,
                    address = "Alter Tivoli, Aachen"
                ),
                Activity(
                    name = "Oecher Eistreff",
                    category = ActivityCategory.ICE_CREAM,
                    latitude = 50.7629,
                    longitude = 6.1018,
                    isIndoor = true,
                    address = "Bismarckstraße 72, 52066 Aachen"
                ),
                Activity(
                    name = "Wingerbergerhoeve IJs & Fruit",
                    category = ActivityCategory.ICE_CREAM,
                    latitude = 50.7860,
                    longitude = 5.9720,
                    isIndoor = false,
                    address = "Vijlen, Netherlands"
                ),
                Activity(
                    name = "De Helenahoeve",
                    category = ActivityCategory.ICE_CREAM,
                    latitude = 50.7890,
                    longitude = 5.9340,
                    isIndoor = false,
                    address = "Slenaken, Netherlands"
                ),
                Activity(
                    name = "Leana und Luise",
                    category = ActivityCategory.ICE_CREAM,
                    latitude = 50.7745,
                    longitude = 6.0860,
                    isIndoor = true,
                    address = "Aachen"
                ),
                Activity(
                    name = "Eisdiele Tasin",
                    category = ActivityCategory.ICE_CREAM,
                    latitude = 50.7755,
                    longitude = 6.0870,
                    isIndoor = true,
                    address = "Aachen"
                )
            )

            sampleActivities.forEach { viewModel.insertActivity(it) }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "${sampleActivities.size} sample activities added",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
