package com.example.freizeit.ui.activity

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.freizeit.R
import com.example.freizeit.databinding.ActivityActivityEditBinding
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory
import com.example.freizeit.ui.viewmodel.MainViewModel
import com.example.freizeit.util.GeocodingService
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class ActivityEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActivityEditBinding
    private val viewModel: MainViewModel by viewModels()
    private val geocodingService = GeocodingService()

    private var activityId: Long? = null
    private var currentActivity: Activity? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var address: String? = null
    private var useCoordinateInput = false

    private var enableWalk = true
    private var enableBike = true
    private var enableTransit = true
    private var enableCar = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().userAgentValue = packageName

        activityId = intent.getLongExtra("activity_id", -1).takeIf { it != -1L }

        setupToolbar()
        setupCategoryDropdown()
        setupButtons()

        activityId?.let { loadActivity(it) }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (activityId != null) {
            getString(R.string.activity_edit_title)
        } else {
            getString(R.string.activity_add_title)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCategoryDropdown() {
        val categories = ActivityCategory.values().map { getCategoryName(it) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryInput.setAdapter(adapter)

        intent.getStringExtra("default_category")?.let {
            val category = ActivityCategory.valueOf(it)
            binding.categoryInput.setText(getCategoryName(category), false)
        }
    }

    private fun setupButtons() {
        binding.addressModeButton.setOnClickListener {
            useCoordinateInput = false
            binding.addressInputLayout.visibility = View.VISIBLE
            binding.coordsInputLayout.visibility = View.GONE
        }

        binding.coordsModeButton.setOnClickListener {
            useCoordinateInput = true
            binding.addressInputLayout.visibility = View.GONE
            binding.coordsInputLayout.visibility = View.VISIBLE

            latitude?.let { lat ->
                longitude?.let { lon ->
                    binding.latitudeInput.setText(lat.toString())
                    binding.longitudeInput.setText(lon.toString())
                }
            }
        }

        binding.inputModeToggle.check(R.id.addressModeButton)

        binding.walkToggle.setOnCheckedChangeListener { _, isChecked -> enableWalk = isChecked }
        binding.bikeToggle.setOnCheckedChangeListener { _, isChecked -> enableBike = isChecked }
        binding.transitToggle.setOnCheckedChangeListener { _, isChecked -> enableTransit = isChecked }
        binding.carToggle.setOnCheckedChangeListener { _, isChecked -> enableCar = isChecked }

        binding.geocodeButton.setOnClickListener {
            if (useCoordinateInput) geocodeFromCoordinates() else geocodeAddress()
        }

        binding.deleteButton.setOnClickListener {
            confirmDeleteActivity()
        }
    }

    private fun geocodeFromCoordinates() {
        val latText = binding.latitudeInput.text.toString().trim()
        val lonText = binding.longitudeInput.text.toString().trim()

        if (latText.isEmpty() || lonText.isEmpty()) {
            Toast.makeText(this, R.string.coords_required, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            latitude = latText.toDouble()
            longitude = lonText.toDouble()

            if (latitude!! < -90 || latitude!! > 90) {
                Toast.makeText(this, R.string.coords_lat_range, Toast.LENGTH_SHORT).show()
                return
            }
            if (longitude!! < -180 || longitude!! > 180) {
                Toast.makeText(this, R.string.coords_lon_range, Toast.LENGTH_SHORT).show()
                return
            }

            address = null
            showMap(latitude!!, longitude!!)
            Toast.makeText(this, R.string.coords_accepted, Toast.LENGTH_SHORT).show()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, R.string.coords_required, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadActivity(id: Long) {
        lifecycleScope.launch {
            val activity = viewModel.getActivityById(id)
            activity?.let {
                currentActivity = it
                binding.nameInput.setText(it.name)
                binding.categoryInput.setText(getCategoryName(it.category), false)
                binding.indoorRadio.isChecked = it.isIndoor
                binding.outdoorRadio.isChecked = !it.isIndoor

                latitude = it.latitude
                longitude = it.longitude
                address = it.address

                enableWalk = it.enableWalk
                enableBike = it.enableBike
                enableTransit = it.enableTransit
                enableCar = it.enableCar

                binding.walkToggle.isChecked = it.enableWalk
                binding.bikeToggle.isChecked = it.enableBike
                binding.transitToggle.isChecked = it.enableTransit
                binding.carToggle.isChecked = it.enableCar

                binding.deleteButton.visibility = View.VISIBLE

                showMap(it.latitude, it.longitude)
            }
        }
    }

    private fun geocodeAddress() {
        val street = binding.streetInput.text.toString()
        val zip = binding.zipInput.text.toString()
        val city = binding.cityInput.text.toString()

        if (street.isEmpty() || zip.isEmpty() || city.isEmpty()) {
            Toast.makeText(this, R.string.favorite_location_address_required, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.geocodeButton.isEnabled = false
            binding.geocodeButton.text = getString(R.string.favorite_location_geocoding)

            val result = geocodingService.geocodeAddress(street, "", zip, city)

            if (result != null) {
                latitude = result.latitude
                longitude = result.longitude
                address = "$street, $zip $city"
                showMap(result.latitude, result.longitude)
                Toast.makeText(this@ActivityEditActivity, R.string.location_found, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@ActivityEditActivity,
                    R.string.favorite_location_geocoding_error,
                    Toast.LENGTH_SHORT
                ).show()
            }

            binding.geocodeButton.isEnabled = true
            binding.geocodeButton.text = getString(R.string.favorite_location_set_location)
        }
    }

    private fun showMap(latitude: Double, longitude: Double) {
        binding.mapCard.visibility = View.VISIBLE
        val mapView = binding.mapView

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val point = GeoPoint(latitude, longitude)
        mapView.controller.setCenter(point)
        mapView.controller.setZoom(18.0)

        mapView.overlays.clear()

        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = getString(R.string.activity_marker_title)
        mapView.overlays.add(marker)

        viewModel.currentLocation.value?.let { (userLat, userLon) ->
            val userPoint = GeoPoint(userLat, userLon)

            val userMarker = Marker(mapView)
            userMarker.position = userPoint
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            userMarker.title = getString(R.string.marker_your_location)
            userMarker.icon = resources.getDrawable(R.drawable.ic_location, null)?.apply {
                setTint(resources.getColor(R.color.turquoise_primary, null))
            }
            mapView.overlays.add(userMarker)
        }

        mapView.invalidate()
    }

    private fun saveActivity() {
        val name = binding.nameInput.text.toString()
        val categoryText = binding.categoryInput.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.activity_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (categoryText.isEmpty()) {
            Toast.makeText(this, R.string.activity_category_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (latitude == null || longitude == null) {
            Toast.makeText(this, R.string.activity_location_required, Toast.LENGTH_SHORT).show()
            return
        }

        val category = ActivityCategory.values().find { getCategoryName(it) == categoryText }
            ?: ActivityCategory.WALK

        val isIndoor = binding.indoorRadio.isChecked

        val activity = Activity(
            id = activityId ?: 0,
            name = name,
            category = category,
            isIndoor = isIndoor,
            latitude = latitude!!,
            longitude = longitude!!,
            address = address,
            enableWalk = enableWalk,
            enableBike = enableBike,
            enableTransit = enableTransit,
            enableCar = enableCar
        )

        lifecycleScope.launch {
            if (activityId != null) {
                viewModel.updateActivity(activity)
                Toast.makeText(this@ActivityEditActivity, R.string.activity_updated, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.insertActivity(activity)
                Toast.makeText(this@ActivityEditActivity, R.string.activity_saved, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
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

    private fun confirmDeleteActivity() {
        AlertDialog.Builder(this)
            .setTitle(R.string.activity_delete)
            .setMessage(R.string.activity_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteActivity() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteActivity() {
        currentActivity?.let { activity ->
            lifecycleScope.launch {
                viewModel.deleteActivity(activity)
                Toast.makeText(this@ActivityEditActivity, R.string.activity_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_activity_edit, menu)
        menu.findItem(R.id.action_save)?.isVisible = true
        menu.findItem(R.id.action_delete)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveActivity()
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
}
