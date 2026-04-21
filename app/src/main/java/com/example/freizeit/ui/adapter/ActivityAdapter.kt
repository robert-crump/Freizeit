package com.example.freizeit.ui.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.freizeit.R
import com.example.freizeit.databinding.ItemActivityBinding
import com.example.freizeit.data.entity.Activity
import com.example.freizeit.data.entity.ActivityCategory

class ActivityAdapter(
    private val userLatitude: Double,
    private val userLongitude: Double,
    private val onActivityClick: (Activity) -> Unit
) : ListAdapter<Activity, ActivityAdapter.ActivityViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val binding = ItemActivityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActivityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ActivityViewHolder(
        private val binding: ItemActivityBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: Activity) {
            val context = binding.root.context

            binding.activityName.text = activity.name
            binding.categoryIcon.setImageResource(getCategoryIcon(activity.category))

            val distance = activity.getDistanceTo(userLatitude, userLongitude)
            val distanceText = if (distance < 1.0) {
                val roundedMeters = ((distance * 1000).toInt() / 10) * 10
                "$roundedMeters m"
            } else {
                String.format("%.1f km", distance)
            }

            val categoryName = getCategoryName(context, activity.category)
            val typeText = if (activity.isIndoor) {
                context.getString(R.string.activity_indoor)
            } else {
                context.getString(R.string.activity_outdoor)
            }
            binding.activityDetails.text = "$categoryName | $typeText | $distanceText"

            binding.mapsIcon.setOnClickListener {
                openInGoogleMaps(activity)
            }

            binding.activityCard.setOnClickListener {
                onActivityClick(activity)
            }
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

            if (intent.resolveActivity(binding.root.context.packageManager) != null) {
                binding.root.context.startActivity(intent)
            } else {
                val browserUri = if (!activity.address.isNullOrEmpty()) {
                    val query = Uri.encode("${activity.name}, ${activity.address}")
                    "https://www.google.com/maps/search/?api=1&query=$query"
                } else {
                    "https://www.google.com/maps/search/?api=1&query=${activity.latitude},${activity.longitude}"
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUri))
                binding.root.context.startActivity(browserIntent)
            }
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

    class ActivityDiffCallback : DiffUtil.ItemCallback<Activity>() {
        override fun areItemsTheSame(oldItem: Activity, newItem: Activity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Activity, newItem: Activity): Boolean {
            return oldItem == newItem
        }
    }
}
