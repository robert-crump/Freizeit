package com.example.freizeit.util

import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.Poi

/** One user-added [customPoi] that looks like it's the same real place as a freshly imported
 *  OSM [poi] — issue #49's reimport-time merge detection. Surfaced to Settings as a confirm/
 *  dismiss prompt; confirming re-keys [customPoi]'s Visit/Verdict rows onto [poi].id and drops
 *  the `custom_poi` row (see [com.example.freizeit.data.repository.PoiRepository.mergeCustomPoiInto]). */
data class MergeCandidate(val customPoi: CustomPoi, val poi: Poi)

/**
 * Finds custom POIs that a `.pbf` reimport (#43's epic) just made redundant: a newly-upserted
 * `poi` row of the same category, close enough by distance, with a similar-enough name. Distance
 * +category alone (mirroring [CustomPoiProximity]'s creation-time dedup check) is too loose here
 * — two different same-category places are common within a short walk of each other — so a fuzzy
 * name check (issue #49's own spec) via [NameSimilarity] is required too, unlike that check.
 */
object CustomPoiMergeMatch {

    /** Same generosity as [CustomPoiProximity.WARNING_THRESHOLD_METERS] — the two checks are
     *  answering a similar question ("is this close enough to plausibly be the same place?"),
     *  just at different points in the app's lifecycle (creation-time vs. reimport-time). */
    const val THRESHOLD_METERS = CustomPoiProximity.WARNING_THRESHOLD_METERS

    /** One candidate per [customPois] entry at most — its single nearest same-category,
     *  similarly-named [newPois] match, if any clears both thresholds. A custom POI with no
     *  qualifying match contributes nothing, so an import with no likely duplicates returns an
     *  empty list and the caller shows no prompt at all. */
    fun findCandidates(
        customPois: List<CustomPoi>,
        newPois: List<Poi>,
        thresholdMeters: Double = THRESHOLD_METERS,
        nameSimilarityThreshold: Double = NameSimilarity.SIMILARITY_THRESHOLD
    ): List<MergeCandidate> =
        customPois.mapNotNull { custom ->
            newPois
                .asSequence()
                .filter { it.category == custom.category }
                .filter { NameSimilarity.isSimilar(custom.name, it.name.orEmpty(), nameSimilarityThreshold) }
                .map { it to GeoDistance.metersBetween(custom.lat, custom.lon, it.lat, it.lon) }
                .filter { it.second <= thresholdMeters }
                .minByOrNull { it.second }
                ?.let { (poi, _) -> MergeCandidate(custom, poi) }
        }
}
