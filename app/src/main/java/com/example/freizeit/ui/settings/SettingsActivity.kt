package com.example.freizeit.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.freizeit.R
import com.example.freizeit.databinding.ActivitySettingsBinding
import com.example.freizeit.data.AppDatabase
import com.example.freizeit.data.entity.FavoriteLocation
import com.example.freizeit.data.repository.ActivityRepository
import com.example.freizeit.data.repository.FavoriteLocationRepository
import com.example.freizeit.ui.adapter.FavoriteLocationAdapter
import com.example.freizeit.ui.viewmodel.MainViewModel
import com.example.freizeit.util.DatabaseExportImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var favoriteAdapter: FavoriteLocationAdapter

    private lateinit var activityRepository: ActivityRepository
    private lateinit var favoriteLocationRepository: FavoriteLocationRepository

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportDatabase(it) }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importDatabase(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        activityRepository = ActivityRepository(database.activityDao())
        favoriteLocationRepository = FavoriteLocationRepository(database.favoriteLocationDao())

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()
        updateMapStorageInfo()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        favoriteAdapter = FavoriteLocationAdapter(
            onEditClick = { location -> openFavoriteLocationEdit(location) },
            onDeleteClick = { location -> confirmDeleteFavoriteLocation(location) }
        )

        binding.favoriteLocationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = favoriteAdapter
        }
    }

    private fun setupButtons() {
        binding.exportButton.setOnClickListener {
            createDocumentLauncher.launch("freizeit_backup.json")
        }

        binding.importButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json"))
        }

        binding.addFavoriteButton.setOnClickListener {
            openFavoriteLocationEdit(null)
        }

        binding.downloadMapsButton.setOnClickListener {
            downloadMapsForFavorites()
        }
    }

    private fun observeViewModel() {
        viewModel.allFavoriteLocations.observe(this) { locations ->
            if (locations.isEmpty()) {
                binding.emptyFavoritesText.visibility = View.VISIBLE
                binding.favoriteLocationsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyFavoritesText.visibility = View.GONE
                binding.favoriteLocationsRecyclerView.visibility = View.VISIBLE
                favoriteAdapter.submitList(locations)
            }
        }
    }

    private fun exportDatabase(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val activities = activityRepository.getAllActivitiesList()
                val favoriteLocations = favoriteLocationRepository.getAllFavoriteLocationsList()

                val json = DatabaseExportImport.exportToJson(activities, favoriteLocations)

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }

                Toast.makeText(this@SettingsActivity, R.string.settings_export_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Toast.makeText(this@SettingsActivity, R.string.settings_export_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importDatabase(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }

                if (json != null) {
                    val data = DatabaseExportImport.importFromJson(json)

                    if (data != null) {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(R.string.settings_import_confirm_title)
                            .setMessage(R.string.settings_import_confirm_message)
                            .setPositiveButton(R.string.settings_import_confirm_button) { _, _ ->
                                performImport(data.activities, data.favoriteLocations)
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    } else {
                        Toast.makeText(this@SettingsActivity, R.string.settings_import_error, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                Toast.makeText(this@SettingsActivity, R.string.settings_import_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performImport(
        activities: List<com.example.freizeit.data.entity.Activity>,
        favoriteLocations: List<FavoriteLocation>
    ) {
        lifecycleScope.launch {
            try {
                activityRepository.deleteAll()
                favoriteLocationRepository.deleteAll()

                activities.forEach { activity -> activityRepository.insert(activity) }
                favoriteLocations.forEach { location -> favoriteLocationRepository.insert(location) }

                Toast.makeText(this@SettingsActivity, R.string.settings_import_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Perform import failed", e)
                Toast.makeText(this@SettingsActivity, R.string.settings_import_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFavoriteLocationEdit(location: FavoriteLocation?) {
        val intent = Intent(this, FavoriteLocationEditActivity::class.java)
        location?.let {
            intent.putExtra("location_id", it.id)
        }
        startActivity(intent)
    }

    private fun confirmDeleteFavoriteLocation(location: FavoriteLocation) {
        AlertDialog.Builder(this)
            .setTitle(R.string.favorite_location_delete)
            .setMessage(R.string.favorite_location_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteFavoriteLocation(location) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteFavoriteLocation(location: FavoriteLocation) {
        lifecycleScope.launch {
            favoriteLocationRepository.delete(location)
            Toast.makeText(this@SettingsActivity, R.string.settings_favorite_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadMapsForFavorites() {
        val locations = viewModel.allFavoriteLocations.value ?: emptyList()

        if (locations.isEmpty()) {
            Toast.makeText(this, R.string.settings_no_favorites, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(
            this,
            getString(R.string.settings_download_started, locations.size),
            Toast.LENGTH_LONG
        ).show()

        lifecycleScope.launch {
            try {
                val mapView = withContext(Dispatchers.Main) {
                    org.osmdroid.views.MapView(this@SettingsActivity)
                }

                withContext(Dispatchers.IO) {
                    locations.forEach { location ->
                        val north = location.latitude + 0.025
                        val south = location.latitude - 0.025
                        val east = location.longitude + 0.025
                        val west = location.longitude - 0.025

                        val boundingBox = org.osmdroid.util.BoundingBox(north, east, south, west)

                        val cacheManager = withContext(Dispatchers.Main) {
                            org.osmdroid.tileprovider.cachemanager.CacheManager(mapView)
                        }

                        cacheManager.downloadAreaAsync(
                            this@SettingsActivity,
                            boundingBox,
                            12,
                            16,
                            object : org.osmdroid.tileprovider.cachemanager.CacheManager.CacheManagerCallback {
                                override fun onTaskComplete() {
                                    Log.d(TAG, "Download complete for ${location.name}")
                                }

                                override fun onTaskFailed(errors: Int) {
                                    Log.e(TAG, "Download failed for ${location.name}: $errors errors")
                                }

                                override fun updateProgress(
                                    progress: Int,
                                    currentZoomLevel: Int,
                                    zoomMin: Int,
                                    zoomMax: Int
                                ) {
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        binding.downloadStatusText.text = this@SettingsActivity.getString(
                                            R.string.settings_download_progress,
                                            progress,
                                            currentZoomLevel,
                                            zoomMax
                                        )
                                    }
                                }

                                override fun downloadStarted() {
                                    Log.d(TAG, "Download started for ${location.name}")
                                }

                                override fun setPossibleTilesInArea(total: Int) {
                                    Log.d(TAG, "$total tiles to download")
                                }
                            }
                        )

                        kotlinx.coroutines.delay(2000)
                    }
                }

                withContext(Dispatchers.Main) {
                    val cachePath = org.osmdroid.config.Configuration.getInstance().osmdroidTileCache
                    val cacheDir = java.io.File(cachePath.absolutePath)
                    val totalSizeMB = calculateDirectorySize(cacheDir) / (1024.0 * 1024.0)

                    binding.downloadStatusText.text = getString(
                        R.string.settings_maps_status,
                        locations.size,
                        totalSizeMB
                    )

                    Toast.makeText(this@SettingsActivity, R.string.settings_maps_downloaded, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.settings_maps_error, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun calculateDirectorySize(directory: java.io.File): Long {
        var size: Long = 0

        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }

        return size
    }

    private fun updateMapStorageInfo() {
        lifecycleScope.launch {
            try {
                val cachePath = org.osmdroid.config.Configuration.getInstance().osmdroidTileCache
                val cacheDir = java.io.File(cachePath.absolutePath)
                val totalSizeMB = calculateDirectorySize(cacheDir) / (1024.0 * 1024.0)

                val locations = viewModel.allFavoriteLocations.value ?: emptyList()
                binding.downloadStatusText.text = getString(
                    R.string.settings_maps_status,
                    locations.size,
                    totalSizeMB
                )
            } catch (e: Exception) {
                binding.downloadStatusText.text = getString(R.string.settings_status_no_maps)
            }
        }
    }
}
