package com.example.freizeit.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.freizeit.databinding.ItemFavoriteLocationBinding
import com.example.freizeit.data.entity.FavoriteLocation

class FavoriteLocationAdapter(
    private val onEditClick: (FavoriteLocation) -> Unit,
    private val onDeleteClick: (FavoriteLocation) -> Unit
) : ListAdapter<FavoriteLocation, FavoriteLocationAdapter.ViewHolder>(FavoriteLocationDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavoriteLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemFavoriteLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(location: FavoriteLocation) {
            // Zeile 1: Name (groß, fett)
            binding.favoriteName.text = location.name

            // Zeile 2: Straße + Hausnummer (klein, grau)
            binding.favoriteStreet.text = "${location.street} ${location.houseNumber}"

            // Zeile 3: PLZ + Stadt (klein, grau)
            binding.favoriteCity.text = "${location.zipCode} ${location.city}"

            binding.editButton.setOnClickListener {
                onEditClick(location)
            }

            binding.deleteButton.setOnClickListener {
                onDeleteClick(location)
            }
        }
    }
    
    class FavoriteLocationDiffCallback : DiffUtil.ItemCallback<FavoriteLocation>() {
        override fun areItemsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: FavoriteLocation, newItem: FavoriteLocation): Boolean {
            return oldItem == newItem
        }
    }

}
