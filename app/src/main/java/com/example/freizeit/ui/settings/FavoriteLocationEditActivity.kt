package com.example.freizeit.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.freizeit.R
import com.example.freizeit.databinding.ActivityFavoriteLocationEditBinding
import com.example.freizeit.data.AppDatabase
import com.example.freizeit.data.entity.FavoriteLocation
import com.example.freizeit.data.repository.FavoriteLocationRepository
import com.example.freizeit.util.GeocodingService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class FavoriteLocationEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFavoriteLocationEditBinding
    private lateinit var repository: FavoriteLocationRepository
    private val geocodingService = GeocodingService()

    private var locationId: Long? = null
    private var currentLocation: FavoriteLocation? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var address: String? = null

    private enum class InputMethod {
        CURRENT_LOCATION, ADDRESS, COORDINATES
    }
    private var currentInputMethod = InputMethod.ADDRESS

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteLocationEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().userAgentValue = packageName

        val database = AppDatabase.getDatabase(this)
        repository = FavoriteLocationRepository(database.favoriteLocationDao())

        locationId = intent.getLongExtra("location_id", -1).takeIf { it != -1L }

        setupToolbar()
        setupButtons()

        locationId?.let { loadLocation(it) }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (locationId != null) {
            getString(R.string.favorite_location_edit)
        } else {
            getString(R.string.favorite_location_add)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        binding.inputMethodToggle.check(R.id.addressButton)

        binding.currentLocationButton.setOnClickListener {
            currentInputMethod = InputMethod.CURRENT_LOCATION
            updateInputMethodUI()
            requestCurrentLocation()
        }

        binding.addressButton.setOnClickListener {
            currentInputMethod = InputMethod.ADDRESS
            updateInputMethodUI()
        }

        binding.coordinatesButton.setOnClickListener {
            currentInputMethod = InputMethod.COORDINATES
            updateInputMethodUI()
            latitude?.let { lat ->
                longitude?.let { lon ->
                    binding.latitudeInput.setText(lat.toString())
                    binding.longitudeInput.setText(lon.toString())
                }
            }
        }

        binding.geocodeButton.setOnClickListener {
            when (currentInputMethod) {
                InputMethod.CURRENT_LOCATION -> requestCurrentLocation()
                InputMethod.ADDRESS -> geocodeAddress()
                InputMethod.COORDINATES -> geocodeFromCoordinates()
            }
        }

        binding.saveButton.setOnClickListener {
            saveLocation()
        }
    }

    private fun updateInputMethodUI() {
        when (currentInputMethod) {
            InputMethod.CURRENT_LOCATION -> {
                binding.addressInputLayout.visibility = View.GONE
                binding.coordinatesInputLayout.visibility = View.GONE
                binding.geocodeButton.text = getString(R.string.favorite_location_use_current)
            }
            InputMethod.ADDRESS -> {
                binding.addressInputLayout.visibility = View.VISIBLE
                binding.coordinatesInputLayout.visibility = View.GONE
                binding.geocodeButton.text = getString(R.string.favorite_location_set_location)
            }
            InputMethod.COORDINATES -> {
                binding.addressInputLayout.visibility = View.GONE
                binding.coordinatesInputLayout.visibility = View.VISIBLE
                binding.geocodeButton.text = getString(R.string.favorite_location_accept_coords)
            }
        }
    }

    private fun requestCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    showMap(location.latitude, location.longitude)
                    Toast.makeText(this, R.string.location_found, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.location_not_found, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.location_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadLocation(id: Long) {
        lifecycleScope.launch {
            val location = repository.getFavoriteLocationById(id)
            location?.let {
                currentLocation = it
                binding.nameInput.setText(it.name)
                binding.streetInput.setText("${it.street} ${it.houseNumber}")
                binding.zipInput.setText(it.zipCode)
                binding.cityInput.setText(it.city)
                latitude = it.latitude
                longitude = it.longitude
                showMap(it.latitude, it.longitude)
            }
        }
    }

    private fun geocodeAddress() {
        val street = binding.streetInput.text.toString().trim()
        val zip = binding.zipInput.text.toString().trim()
        val city = binding.cityInput.text.toString().trim()

        if (street.isEmpty() || zip.isEmpty() || city.isEmpty()) {
            Toast.makeText(this, R.string.favorite_location_address_required, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.geocodeButton.isEnabled = false
            binding.geocodeButton.text = getString(R.string.favorite_location_geocoding)

            val parts = street.split(" ")
            val streetName = parts.dropLast(1).joinToString(" ")
            val houseNumber = parts.lastOrNull() ?: ""

            val result = geocodingService.geocodeAddress(streetName, houseNumber, zip, city)

            if (result != null) {
                latitude = result.latitude
                longitude = result.longitude
                address = "$street, $zip $city"
                showMap(result.latitude, result.longitude)
                Toast.makeText(this@FavoriteLocationEditActivity, R.string.location_found, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@FavoriteLocationEditActivity,
                    R.string.favorite_location_geocoding_error,
                    Toast.LENGTH_SHORT
                ).show()
            }

            binding.geocodeButton.isEnabled = true
            binding.geocodeButton.text = getString(R.string.favorite_location_set_location)
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

    private fun showMap(lat: Double, lon: Double) {
        binding.mapCard.visibility = View.VISIBLE

        val center = GeoPoint(lat, lon)
        binding.mapView.controller.setCenter(center)
        binding.mapView.controller.setZoom(16.0)

        binding.mapView.overlays.clear()

        val marker = Marker(binding.mapView)
        marker.position = center
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = binding.nameInput.text.toString()
        binding.mapView.overlays.add(marker)

        val circle = Polygon(binding.mapView)
        circle.points = Polygon.pointsAsCircle(center, 300.0)
        circle.fillColor = 0x2000BCD4
        circle.strokeColor = getColor(R.color.turquoise_primary)
        circle.strokeWidth = 2f
        binding.mapView.overlays.add(circle)

        binding.mapView.invalidate()
    }

    private fun saveLocation() {
        val name = binding.nameInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.favorite_location_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (latitude == null || longitude == null) {
            Toast.makeText(this, R.string.favorite_location_location_required, Toast.LENGTH_SHORT).show()
            return
        }

        val street = binding.streetInput.text.toString().trim()
        val zip = binding.zipInput.text.toString().trim()
        val city = binding.cityInput.text.toString().trim()

        val streetName: String
        val houseNumber: String

        if (street.isNotEmpty()) {
            val parts = street.split(" ")
            streetName = parts.dropLast(1).joinToString(" ").ifEmpty { street }
            houseNumber = if (parts.size > 1) parts.last() else ""
        } else {
            streetName = ""
            houseNumber = ""
        }

        val location = FavoriteLocation(
            id = locationId ?: 0,
            name = name,
            street = streetName,
            houseNumber = houseNumber,
            zipCode = zip,
            city = city,
            latitude = latitude!!,
            longitude = longitude!!
        )

        lifecycleScope.launch {
            if (locationId != null) {
                repository.update(location)
            } else {
                repository.insert(location)
            }

            Toast.makeText(this@FavoriteLocationEditActivity, R.string.favorite_location_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun deleteLocation() {
        currentLocation?.let { location ->
            AlertDialog.Builder(this)
                .setTitle(R.string.favorite_location_delete)
                .setMessage(R.string.favorite_location_delete_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        repository.delete(location)
                        Toast.makeText(
                            this@FavoriteLocationEditActivity,
                            R.string.favorite_location_delete,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (locationId != null) {
            menuInflater.inflate(R.menu.menu_favorite_location_edit, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteLocation()
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
