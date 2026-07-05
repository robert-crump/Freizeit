package com.example.freizeit.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.freizeit.data.FreizeitDatabase
import com.example.freizeit.data.PoiJsonParser
import com.example.freizeit.data.PoiParseException
import com.example.freizeit.data.dao.CategoryCount
import com.example.freizeit.data.entity.ImportInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

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
     * @return number of places in the imported file
     * @throws PoiParseException on unreadable or malformed files
     */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
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
        parsed.pois.size
    }
}
