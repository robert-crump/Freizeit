package com.example.freizeit.data

import com.example.freizeit.data.entity.Poi
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.Reader

class PoiParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ParsedPoiFile(
    val generatedAt: String?,
    val pois: List<Poi>
)

/**
 * Strict parser for the extraction file format documented in
 * tools/poi_extraction/README.md. Throws [PoiParseException] on anything
 * that does not match; the import only writes to the database after the
 * whole file parsed successfully.
 */
object PoiJsonParser {

    fun parse(reader: Reader): ParsedPoiFile {
        val root = try {
            JsonParser.parseReader(reader)
        } catch (e: JsonParseException) {
            throw PoiParseException("File is not valid JSON", e)
        }
        if (!root.isJsonObject) {
            throw PoiParseException("File is not a POI export (expected a JSON object)")
        }
        val obj = root.asJsonObject

        val poisElement = obj.get("pois")
            ?: throw PoiParseException("File is not a POI export (missing \"pois\" list)")
        if (!poisElement.isJsonArray) {
            throw PoiParseException("File is not a POI export (\"pois\" is not a list)")
        }

        val pois = poisElement.asJsonArray.mapIndexed { index, element ->
            parsePoi(element, index)
        }

        return ParsedPoiFile(
            generatedAt = obj.optString("generated"),
            pois = pois
        )
    }

    private fun parsePoi(element: JsonElement, index: Int): Poi {
        if (!element.isJsonObject) {
            throw PoiParseException("POI #$index is not an object")
        }
        val obj = element.asJsonObject
        return Poi(
            id = obj.requiredString("id", index),
            category = obj.requiredString("category", index),
            lat = obj.requiredDouble("lat", index),
            lon = obj.requiredDouble("lon", index),
            name = obj.optString("name"),
            openingHours = obj.optString("opening_hours"),
            street = obj.optString("street"),
            housenumber = obj.optString("housenumber"),
            postcode = obj.optString("postcode"),
            city = obj.optString("city")
        )
    }

    private fun JsonObject.requiredString(key: String, index: Int): String {
        val value = get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw PoiParseException("POI #$index has no \"$key\"")
        }
        val s = value.asString
        if (s.isBlank()) throw PoiParseException("POI #$index has a blank \"$key\"")
        return s
    }

    private fun JsonObject.requiredDouble(key: String, index: Int): Double {
        val value = get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw PoiParseException("POI #$index has no numeric \"$key\"")
        }
        return value.asDouble
    }

    private fun JsonObject.optString(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString.takeIf { it.isNotBlank() }
    }
}
