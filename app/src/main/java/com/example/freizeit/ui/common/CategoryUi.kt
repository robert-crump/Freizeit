package com.example.freizeit.ui.common

import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Stable display order matching the extraction registry; unknown categories go last. */
val CATEGORY_ORDER = listOf("playground", "park", "cafe", "restaurant", "ice_cream")

fun categoryOrderIndex(category: String): Int {
    val index = CATEGORY_ORDER.indexOf(category)
    return if (index >= 0) index else CATEGORY_ORDER.size
}

fun categoryDisplayName(category: String): String =
    category.replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun categoryColor(category: String): Color = when (category) {
    "playground" -> Color(0xFFF57C00)
    "park" -> Color(0xFF2E7D32)
    "cafe" -> Color(0xFF6D4C41)
    "restaurant" -> Color(0xFFC62828)
    "ice_cream" -> Color(0xFFD81B60)
    else -> Color(0xFF546E7A)
}
