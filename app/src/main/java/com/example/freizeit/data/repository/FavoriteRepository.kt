package com.example.freizeit.data.repository

import com.example.freizeit.data.dao.FavoriteDao
import com.example.freizeit.data.entity.Favorite
import com.example.freizeit.util.AddressResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FavoriteResolveException(message: String) : Exception(message)

class FavoriteRepository(
    private val dao: FavoriteDao,
    private val addressResolver: AddressResolver
) {
    val favorites: Flow<List<Favorite>> = dao.observeAll()

    /** @throws FavoriteResolveException when [address] doesn't resolve to coordinates */
    suspend fun add(name: String, address: String): Favorite = withContext(Dispatchers.IO) {
        val coords = addressResolver.resolve(address)
            ?: throw FavoriteResolveException("Couldn't find that address")
        val favorite = Favorite(name = name, address = address, lat = coords.lat, lon = coords.lon)
        favorite.copy(id = dao.insert(favorite))
    }

    /** @throws FavoriteResolveException when [address] doesn't resolve to coordinates */
    suspend fun update(favorite: Favorite, name: String, address: String): Favorite =
        withContext(Dispatchers.IO) {
            val coords = addressResolver.resolve(address)
                ?: throw FavoriteResolveException("Couldn't find that address")
            val updated = favorite.copy(name = name, address = address, lat = coords.lat, lon = coords.lon)
            dao.update(updated)
            updated
        }

    suspend fun delete(favorite: Favorite) = withContext(Dispatchers.IO) {
        dao.delete(favorite)
    }
}
