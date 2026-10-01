package com.example.freizeit.data

import com.example.freizeit.data.entity.CustomPoi
import com.example.freizeit.data.entity.PoiOverride
import com.example.freizeit.data.entity.Verdict
import com.example.freizeit.data.entity.Visit
import com.example.freizeit.data.repository.ThemeMode
import com.example.freizeit.data.repository.UserSettings
import com.example.freizeit.util.BandLimits
import com.example.freizeit.util.TravelLimits
import com.example.freizeit.util.TravelMode
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.Reader
import java.io.Writer

class BackupParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupData(
    val exportedAt: Long,
    val verdicts: List<Verdict>,
    val overrides: List<PoiOverride>,
    val customPois: List<CustomPoi> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val settings: UserSettings = UserSettings()
)

/**
 * The app's own backup format for all user data (#74): verdicts (favorites among
 * them), the user's edits to places (`poiOverrides`, #73; files written before #73
 * carry name-only `customNames` instead, read as name overrides), user-added
 * custom places (issue #45) — unlike OSM `poi` rows, `custom_poi` has no other
 * source of truth to regenerate from — the check-in history (`visits`) and the
 * user's `settings`. A section an older file lacks reads as empty, or as the
 * default settings. Parsed defensively like [PoiJsonParser] since the file can be
 * hand-edited between export and re-import.
 */
object BackupJsonCodec {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun write(writer: Writer, data: BackupData) {
        val root = JsonObject()
        root.addProperty("exportedAt", data.exportedAt)
        root.add(
            "verdicts",
            JsonArray().apply {
                data.verdicts.forEach { v ->
                    add(
                        JsonObject().apply {
                            addProperty("placeId", v.placeId)
                            addProperty("value", v.value)
                            addProperty("verdictedAt", v.verdictedAt)
                            addProperty("snapshotName", v.snapshotName)
                            addProperty("snapshotLat", v.snapshotLat)
                            addProperty("snapshotLon", v.snapshotLon)
                            addProperty("snapshotCategory", v.snapshotCategory)
                        }
                    )
                }
            }
        )
        root.add(
            "poiOverrides",
            JsonArray().apply {
                data.overrides.forEach { o ->
                    add(
                        JsonObject().apply {
                            addProperty("placeId", o.placeId)
                            addProperty("name", o.name)
                            addProperty("category", o.category)
                            addProperty("street", o.street)
                            addProperty("housenumber", o.housenumber)
                            addProperty("postcode", o.postcode)
                            addProperty("city", o.city)
                            addProperty("openingHours", o.openingHours)
                            addProperty("hidden", o.hidden)
                        }
                    )
                }
            }
        )
        root.add(
            "customPois",
            JsonArray().apply {
                data.customPois.forEach { p ->
                    add(
                        JsonObject().apply {
                            addProperty("id", p.id)
                            addProperty("category", p.category)
                            addProperty("lat", p.lat)
                            addProperty("lon", p.lon)
                            addProperty("name", p.name)
                            addProperty("openingHours", p.openingHours)
                            addProperty("street", p.street)
                            addProperty("housenumber", p.housenumber)
                            addProperty("postcode", p.postcode)
                            addProperty("city", p.city)
                        }
                    )
                }
            }
        )
        root.add(
            "visits",
            JsonArray().apply {
                data.visits.forEach { v ->
                    add(
                        JsonObject().apply {
                            addProperty("id", v.id)
                            addProperty("placeId", v.placeId)
                            addProperty("visitedAt", v.visitedAt)
                            addProperty("source", v.source)
                            addProperty("snapshotName", v.snapshotName)
                            addProperty("snapshotLat", v.snapshotLat)
                            addProperty("snapshotLon", v.snapshotLon)
                            addProperty("snapshotCategory", v.snapshotCategory)
                        }
                    )
                }
            }
        )
        root.add(
            "settings",
            JsonObject().apply {
                addProperty("suggestionRadiusKm", data.settings.suggestionRadiusKm)
                addProperty("notifyFavorites", data.settings.notifyFavorites)
                addProperty("notifyWantToGo", data.settings.notifyWantToGo)
                addProperty("themeMode", data.settings.themeMode.name)
                add(
                    "travelLimits",
                    JsonObject().apply {
                        TravelMode.entries.forEach { mode ->
                            val limits = data.settings.travelLimits.of(mode)
                            add(
                                mode.name.lowercase(),
                                JsonObject().apply {
                                    addProperty("greenMax", limits.greenMax)
                                    addProperty("orangeMax", limits.orangeMax)
                                }
                            )
                        }
                    }
                )
            }
        )
        gson.toJson(root, writer)
    }

    fun read(reader: Reader): BackupData {
        val root = try {
            JsonParser.parseReader(reader)
        } catch (e: JsonParseException) {
            throw BackupParseException("File is not valid JSON", e)
        }
        if (!root.isJsonObject) {
            throw BackupParseException("File is not a Freizeit backup (expected a JSON object)")
        }
        val obj = root.asJsonObject

        val exportedAt = obj.get("exportedAt")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong
            ?: throw BackupParseException("Missing \"exportedAt\"")

        return BackupData(
            exportedAt = exportedAt,
            verdicts = obj.array("verdicts").mapIndexed { i, el -> parseVerdict(el, i) },
            // A pre-#73 file's custom names become name-only overrides; an override for the
            // same place wins.
            overrides = (
                obj.array("customNames").mapIndexed { i, el -> parseCustomName(el, i) } +
                    obj.array("poiOverrides").mapIndexed { i, el -> parseOverride(el, i) }
                ).associateBy { it.placeId }.values.toList(),
            customPois = obj.array("customPois").mapIndexed { i, el -> parseCustomPoi(el, i) },
            visits = obj.array("visits").mapIndexed { i, el -> parseVisit(el, i) },
            settings = parseSettings(obj.get("settings"))
        )
    }

    private fun JsonObject.array(key: String): List<JsonElement> {
        val element = get(key) ?: return emptyList()
        if (!element.isJsonArray) throw BackupParseException("\"$key\" is not a list")
        return element.asJsonArray.toList()
    }

    private fun parseVerdict(element: JsonElement, index: Int): Verdict {
        if (!element.isJsonObject) throw BackupParseException("verdicts[$index] is not an object")
        val o = element.asJsonObject
        return Verdict(
            placeId = o.requiredString("placeId", "verdicts", index),
            value = o.requiredString("value", "verdicts", index),
            verdictedAt = o.requiredLong("verdictedAt", "verdicts", index),
            snapshotName = o.optString("snapshotName"),
            snapshotLat = o.requiredDouble("snapshotLat", "verdicts", index),
            snapshotLon = o.requiredDouble("snapshotLon", "verdicts", index),
            snapshotCategory = o.requiredString("snapshotCategory", "verdicts", index)
        )
    }

    private fun parseVisit(element: JsonElement, index: Int): Visit {
        if (!element.isJsonObject) throw BackupParseException("visits[$index] is not an object")
        val o = element.asJsonObject
        return Visit(
            // A missing id lets Room assign one.
            id = o.optLong("id") ?: 0,
            placeId = o.requiredString("placeId", "visits", index),
            visitedAt = o.requiredLong("visitedAt", "visits", index),
            source = o.optString("source") ?: Visit.SOURCE_MANUAL,
            snapshotName = o.optString("snapshotName"),
            snapshotLat = o.requiredDouble("snapshotLat", "visits", index),
            snapshotLon = o.requiredDouble("snapshotLon", "visits", index),
            snapshotCategory = o.requiredString("snapshotCategory", "visits", index)
        )
    }

    /** Settings are forgiving: a missing or odd value falls back to its default. */
    private fun parseSettings(element: JsonElement?): UserSettings {
        val defaults = UserSettings()
        if (element == null || element.isJsonNull) return defaults
        if (!element.isJsonObject) throw BackupParseException("\"settings\" is not an object")
        val o = element.asJsonObject
        return UserSettings(
            suggestionRadiusKm = o.optLong("suggestionRadiusKm")?.toInt()?.takeIf { it >= 1 }
                ?: defaults.suggestionRadiusKm,
            notifyFavorites = o.optBoolean("notifyFavorites") ?: defaults.notifyFavorites,
            notifyWantToGo = o.optBoolean("notifyWantToGo") ?: defaults.notifyWantToGo,
            themeMode = ThemeMode.entries.firstOrNull { it.name == o.optString("themeMode") }
                ?: defaults.themeMode,
            travelLimits = parseTravelLimits(o.get("travelLimits"), defaults.travelLimits)
        )
    }

    /** Per mode: both limits present, or that mode keeps its default. */
    private fun parseTravelLimits(element: JsonElement?, defaults: TravelLimits): TravelLimits {
        if (element == null || !element.isJsonObject) return defaults
        val o = element.asJsonObject
        return TravelMode.entries.fold(defaults) { limits, mode ->
            val m = o.get(mode.name.lowercase())?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@fold limits
            val green = m.optLong("greenMax")?.toInt() ?: return@fold limits
            val orange = m.optLong("orangeMax")?.toInt() ?: return@fold limits
            limits.with(mode, BandLimits(green, orange).sanitized())
        }
    }

    private fun parseCustomName(element: JsonElement, index: Int): PoiOverride {
        if (!element.isJsonObject) throw BackupParseException("customNames[$index] is not an object")
        val o = element.asJsonObject
        return PoiOverride(
            placeId = o.requiredString("placeId", "customNames", index),
            name = o.requiredString("customName", "customNames", index)
        )
    }

    private fun parseOverride(element: JsonElement, index: Int): PoiOverride {
        if (!element.isJsonObject) throw BackupParseException("poiOverrides[$index] is not an object")
        val o = element.asJsonObject
        return PoiOverride(
            placeId = o.requiredString("placeId", "poiOverrides", index),
            name = o.optString("name"),
            category = o.optString("category"),
            street = o.optString("street"),
            housenumber = o.optString("housenumber"),
            postcode = o.optString("postcode"),
            city = o.optString("city"),
            openingHours = o.optString("openingHours"),
            hidden = o.optBoolean("hidden") ?: false
        )
    }

    private fun parseCustomPoi(element: JsonElement, index: Int): CustomPoi {
        if (!element.isJsonObject) throw BackupParseException("customPois[$index] is not an object")
        val o = element.asJsonObject
        return CustomPoi(
            id = o.requiredString("id", "customPois", index),
            category = o.requiredString("category", "customPois", index),
            lat = o.requiredDouble("lat", "customPois", index),
            lon = o.requiredDouble("lon", "customPois", index),
            name = o.requiredString("name", "customPois", index),
            openingHours = o.optString("openingHours"),
            street = o.optString("street"),
            housenumber = o.optString("housenumber"),
            postcode = o.optString("postcode"),
            city = o.optString("city")
        )
    }

    private fun JsonObject.requiredString(key: String, list: String, index: Int): String {
        val value = get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw BackupParseException("$list[$index] has no \"$key\"")
        }
        return value.asString
    }

    private fun JsonObject.requiredLong(key: String, list: String, index: Int): Long {
        val value = get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw BackupParseException("$list[$index] has no numeric \"$key\"")
        }
        return value.asLong
    }

    private fun JsonObject.requiredDouble(key: String, list: String, index: Int): Double {
        val value = get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw BackupParseException("$list[$index] has no numeric \"$key\"")
        }
        return value.asDouble
    }

    private fun JsonObject.optLong(key: String): Long? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return value.asLong
    }

    private fun JsonObject.optBoolean(key: String): Boolean? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return null
        return value.asBoolean
    }

    private fun JsonObject.optString(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString.takeIf { it.isNotBlank() }
    }
}
