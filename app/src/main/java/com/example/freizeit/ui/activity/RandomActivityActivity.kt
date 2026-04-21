package com.example.freizeit.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.freizeit.R
import com.example.freizeit.databinding.ActivityRandomActivityBinding
import com.example.freizeit.databinding.ItemRandomActivityBinding
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory
import com.example.freizeit.data.entity.TransportMode
import com.example.freizeit.ui.viewmodel.MainViewModel
import com.example.freizeit.util.ActivityFilter

class RandomActivityActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRandomActivityBinding
    private val viewModel: MainViewModel by viewModels()

    private var category: ActivityCategory? = null
    private var shuffledActivities: List<Activity> = emptyList()
    private var cachedActivities: List<Activity> = emptyList()

    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0

    companion object {
        private const val TAG = "RandomActivityActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "onCreate")

        category = intent.getStringExtra("category")?.let { ActivityCategory.valueOf(it) }
        userLatitude = intent.getDoubleExtra("latitude", 0.0)
        userLongitude = intent.getDoubleExtra("longitude", 0.0)

        Log.d(TAG, "Category: ${category?.name ?: "RANDOM"}, Location: $userLatitude, $userLongitude")

        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
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

        viewModel.filterOptions.observe(this) {
            updateActivities()
        }
    }

    private fun updateActivities() {
        if (userLatitude == 0.0 && userLongitude == 0.0 || cachedActivities.isEmpty()) {
            showEmptyState()
            return
        }

        val filterOptions = viewModel.filterOptions.value!!

        val nearbyActivities = cachedActivities.filter { activity ->
            activity.getDistanceTo(userLatitude, userLongitude) < 100.0
        }

        val filteredActivities = ActivityFilter.filterActivities(
            nearbyActivities,
            filterOptions,
            userLatitude,
            userLongitude
        )

        if (filteredActivities.isEmpty()) {
            showEmptyState()
        } else {
            shuffledActivities = filteredActivities.shuffled()
            setupViewPager(userLatitude, userLongitude)
        }
    }

    private fun setupViewPager(userLat: Double, userLon: Double) {
        val adapter = RandomActivityPagerAdapter(shuffledActivities, userLat, userLon)
        binding.viewPager.adapter = adapter
        binding.viewPager.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        binding.activityCounter.text = "1 / ${shuffledActivities.size}"

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.activityCounter.text = "${position + 1} / ${shuffledActivities.size}"
            }
        })
    }

    private fun showEmptyState() {
        binding.viewPager.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
    }

    inner class RandomActivityPagerAdapter(
        private val activities: List<Activity>,
        private val userLat: Double,
        private val userLon: Double
    ) : RecyclerView.Adapter<RandomActivityPagerAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRandomActivityBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(activities[position])
        }

        override fun getItemCount() = activities.size

        inner class ViewHolder(
            private val binding: ItemRandomActivityBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(activity: Activity) {
                val context = binding.root.context

                binding.activityName.text = activity.name
                binding.activityCategory.text = getCategoryName(context, activity.category)

                binding.categoryIcon.setImageResource(getCategoryIcon(activity.category))
                binding.categoryIcon.setColorFilter(context.getColor(android.R.color.black))

                val distance = activity.getDistanceTo(userLat, userLon)

                binding.activityDistance.text = if (distance < 1.0) {
                    val roundedMeters = ((distance * 1000).toInt() / 10) * 10
                    "$roundedMeters m"
                } else {
                    String.format("%.1f km", distance)
                }

                binding.activityType.text = if (activity.isIndoor) {
                    context.getString(R.string.activity_indoor)
                } else {
                    context.getString(R.string.activity_outdoor)
                }

                binding.walkDuration.text = "${activity.getDuration(distance, TransportMode.WALK)} min"
                binding.bikeDuration.text = "${activity.getDuration(distance, TransportMode.BIKE)} min"
                binding.transitDuration.text = "${activity.getDuration(distance, TransportMode.TRANSIT)} min"
                binding.carDuration.text = "${activity.getDuration(distance, TransportMode.CAR)} min"

                binding.mapsIcon.setOnClickListener {
                    openInGoogleMaps(activity, context)
                }

                binding.activityCard.setOnClickListener {
                    openActivityEdit(activity, context)
                }
            }

            private fun openInGoogleMaps(activity: Activity, context: android.content.Context) {
                val uri = "geo:${activity.latitude},${activity.longitude}?q=${activity.latitude},${activity.longitude}(${activity.name})"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                intent.setPackage("com.google.android.apps.maps")

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    context.startActivity(browserIntent)
                }
            }

            private fun openActivityEdit(activity: Activity, context: android.content.Context) {
                val intent = Intent(context, ActivityEditActivity::class.java)
                intent.putExtra("activity_id", activity.id)
                context.startActivity(intent)
            }

            private fun getCategoryName(context: android.content.Context, category: ActivityCategory): String {
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
        }
    }
}
