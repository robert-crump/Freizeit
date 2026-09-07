package com.example.freizeit.util

/**
 * Fuzzy name matching for issue #49's reimport-time merge detection: a custom POI and a freshly
 * imported OSM `poi` are candidates for the same real place even when their names aren't a byte-
 * for-byte match (capitalization, punctuation, "Café" vs "Cafe Central" one being a fuller/looser
 * name for the same spot, or a typo). No existing utility in this codebase does this — pure/
 * dependency-free by design so it stays trivially unit-testable, same as [CustomPoiProximity].
 */
object NameSimilarity {

    /** Below this normalized edit-distance ratio, two names are treated as unrelated even if
     *  the place they're attached to is otherwise a distance+category match. Loose enough to
     *  catch minor typos/wording differences, tight enough that two genuinely different names
     *  ("Café Sonne" vs "Bäckerei Klein") don't false-positive. */
    const val SIMILARITY_THRESHOLD = 0.6

    /** Lowercases and collapses anything that isn't a letter/digit into single spaces, so
     *  punctuation, extra whitespace, and case never affect the comparison. */
    fun normalize(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** True when [a] and [b] look like names for the same place: identical once normalized, one
     *  a substring of the other (e.g. "Café Sonne" inside "Café Sonne Aachen"), or close enough
     *  by normalized edit distance. Either name normalizing to empty (blank, or punctuation-only)
     *  never matches — there's nothing to compare. */
    fun isSimilar(a: String, b: String, threshold: Double = SIMILARITY_THRESHOLD): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb || na.contains(nb) || nb.contains(na)) return true
        return similarityRatio(na, nb) >= threshold
    }

    /** 1.0 for identical strings, descending toward 0.0 as Levenshtein edit distance grows
     *  relative to the longer string's length. Exposed (not just [isSimilar]'s boolean) since
     *  callers may want to rank several candidates by closeness rather than only filter them. */
    fun similarityRatio(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
