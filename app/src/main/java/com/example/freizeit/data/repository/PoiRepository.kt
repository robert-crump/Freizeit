package com.example.freizeit.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.PoiJsonParser
import com.example.freizeit.data.PoiParseException
import com.example.freizeit.data.dao.CategoryCount
import com.example.freizeit.data.entity.ImportInfo
import com.example.freizeit.util.CustomPoiMergeMatch
import com.example.freizeit.util.MergeCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/** [count]: number of places in the imported file, as before #49. [mergeCandidates]: custom POIs
 *  (#45) that this import's newly-upserted `poi` rows look like duplicates of — see
 *  [CustomPoiMergeMatch] — for Settings to prompt the user about, one confirm/dismiss at a time. */
data class ImportResult(val count: Int, val mergeCandidates: List<MergeCandidate>)

class PoiRepository(
    private val context: Context,
    private val db: FreizeitDatabase
) {

    val categoryCounts: Flow<List<CategoryCount>> = db.poiDao().categoryCounts()
    val missingCount: Flow<Int> = db.poiDao().missingCount()
    val importInfo: Flow<ImportInfo?> = db.importInfoDao().observe()

    /**
     * Parses the file at [uri] and applies it to the database in one
     * transaction. Parsing happens entirely before the transaction, so a
     * malformed file leaves the database untouched.
     *
     * Refresh semantics: every place present in the file is upserted with
     * missingFromOsm = false; places absent from the file are deleted unless
     * a verdict exists, in which case they stay flagged missingFromOsm = true.
     *
     * @return the import's place count plus any likely custom-POI merge candidates (#49) it
     *  surfaced — computed once the transaction has committed, against the now-current
     *  `custom_poi` table and the file's parsed rows (exactly the rows that were just upserted).
     * @throws PoiParseException on unreadable or malformed files
     */
    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PoiParseException("Could not open the selected file")
        val parsed = input.use { stream ->
            PoiJsonParser.parse(InputStreamReader(stream, Charsets.UTF_8))
        }
        db.withTransaction {
            db.poiDao().markAllMissing()
            db.poiDao().upsertAll(parsed.pois)
            db.poiDao().deleteUnverdictedMissing()
            db.importInfoDao().upsert(
                ImportInfo(
                    importedAt = System.currentTimeMillis(),
                    fileGeneratedAt = parsed.generatedAt
                )
            )
        }
        val mergeCandidates = CustomPoiMergeMatch.findCandidates(db.customPoiDao().getAll(), parsed.pois)
        ImportResult(parsed.pois.size, mergeCandidates)
    }

    /**
     * Confirms one [MergeCandidate] (#49): re-keys [customPoiId]'s Visit/Verdict rows onto
     * [poiId] — the newly-imported OSM place it was matched to — then drops the now-redundant
     * `custom_poi` row. One transaction so a crash mid-merge can't leave the rows re-keyed but
     * the custom POI still present (or vice versa). Deliberately the mirror image of
     * [com.example.freizeit.ui.map.MapViewModel.commitPendingDelete] (#47), which deletes a
     * custom POI's Visit/Verdict instead of re-keying them.
     */
    suspend fun mergeCustomPoiInto(customPoiId: String, poiId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.verdictDao().rekey(customPoiId, poiId)
            db.visitDao().rekey(customPoiId, poiId)
            db.customPoiDao().delete(customPoiId)
        }
    }
}
