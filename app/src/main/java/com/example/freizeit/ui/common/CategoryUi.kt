package com.example.freizeit.ui.common

import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Stable display order matching the extraction registry; unknown categories go last. */
val CATEGORY_ORDER = listOf(
    "playground", "park", "cafe", "restaurant", "ice_cream",
    "shop", "tourism", "leisure_other", "office", "craft", "historic"
)

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
    // Issue #14's coarse categories (issue #16): hues spread across the gaps left by the
    // five colors above, spaced ~35-45 degrees apart so all 11 stay distinct together in
    // "All POIs" mode.
    "shop" -> Color(0xFF1B7D98)
    "tourism" -> Color(0xFF1B986A)
    "leisure_other" -> Color(0xFF7CA51D)
    "office" -> Color(0xFF1D38A5)
    "craft" -> Color(0xFF511DA5)
    "historic" -> Color(0xFFA01DA5)
    else -> Color(0xFF546E7A)
}
