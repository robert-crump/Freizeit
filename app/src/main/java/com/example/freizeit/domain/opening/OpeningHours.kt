package com.example.freizeit.domain.opening

import java.time.DayOfWeek
import java.time.LocalDateTime

enum class OpenStatus { OPEN, CLOSED, UNKNOWN }

/**
 * Conservative evaluator for OSM `opening_hours` values, implementing the
 * hybrid decision recorded on issue #1: a place is only ever reported CLOSED
 * when its tagged hours are fully understood and say so. Anything the parser
 * does not understand (months, sunrise/sunset, week numbers, fallback rules…)
 * yields UNKNOWN, which callers must treat as "never filter by hours".
 *
 * Supported: "24/7", day ranges/lists (Mo-Fr, Sa,Su, Mo-We,Fr), time spans
 * incl. overnight (18:00-02:00) and split hours (08:00-12:00,14:00-18:00),
 * "off"/"closed", and rule chaining with ";" (later rules override earlier
 * ones for the days they mention). "PH …" rules are skipped: public holidays
 * are not modeled, so they can neither open nor close a place here.
 */
class OpeningHours private constructor(
    private val rules: List<Rule>,
    private val alwaysOpen: Boolean
) {

    /** Minutes since midnight; end > 1440 encodes an overnight span. */
    private data class TimeSpan(val startMinute: Int, val endMinute: Int)

    private data class Rule(
        val days: Set<DayOfWeek>,
        val spans: List<TimeSpan>,
        val off: Boolean
    )

    fun statusAt(dateTime: LocalDateTime): OpenStatus {
        if (alwaysOpen) return OpenStatus.OPEN
        val minute = dateTime.hour * 60 + dateTime.minute

        // Overnight spill from yesterday's last applicable rule.
        val yesterdayRule = rules.lastOrNull { dateTime.dayOfWeek.minus(1L) in it.days }
        if (yesterdayRule != null && !yesterdayRule.off &&
            yesterdayRule.spans.any { it.endMinute > MINUTES_PER_DAY && minute < it.endMinute - MINUTES_PER_DAY }
        ) {
            return OpenStatus.OPEN
        }

        val rule = rules.lastOrNull { dateTime.dayOfWeek in it.days }
            ?: return OpenStatus.CLOSED // parsed fine, day never mentioned
        if (rule.off) return OpenStatus.CLOSED
        if (rule.spans.isEmpty()) return OpenStatus.OPEN // "Sa" alone = open all day
        val open = rule.spans.any { span ->
            if (span.endMinute > MINUTES_PER_DAY) minute >= span.startMinute
            else minute >= span.startMinute && minute < span.endMinute
        }
        return if (open) OpenStatus.OPEN else OpenStatus.CLOSED
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        private val DAY_NAMES = mapOf(
            "Mo" to DayOfWeek.MONDAY, "Tu" to DayOfWeek.TUESDAY, "We" to DayOfWeek.WEDNESDAY,
            "Th" to DayOfWeek.THURSDAY, "Fr" to DayOfWeek.FRIDAY, "Sa" to DayOfWeek.SATURDAY,
            "Su" to DayOfWeek.SUNDAY
        )
        private val DAY_SPEC = Regex("""(Mo|Tu|We|Th|Fr|Sa|Su)(-(Mo|Tu|We|Th|Fr|Sa|Su))?(,(Mo|Tu|We|Th|Fr|Sa|Su)(-(Mo|Tu|We|Th|Fr|Sa|Su))?)*""")
        private val TIME_SPAN = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""")

        /** The one entry point: [OpenStatus.UNKNOWN] for null, blank, or unparseable values. */
        fun statusAt(value: String?, dateTime: LocalDateTime): OpenStatus =
            parse(value)?.statusAt(dateTime) ?: OpenStatus.UNKNOWN

        fun parse(value: String?): OpeningHours? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (text == "24/7") return OpeningHours(emptyList(), alwaysOpen = true)

            val rules = mutableListOf<Rule>()
            for (part in text.split(";")) {
                val rulePart = part.trim()
                if (rulePart.isEmpty()) continue
                if (rulePart.startsWith("PH")) continue // holidays not modeled
                rules += parseRule(rulePart) ?: return null
            }
            return if (rules.isEmpty()) null else OpeningHours(rules, alwaysOpen = false)
        }

        private fun parseRule(rulePart: String): Rule? {
            val firstSpace = rulePart.indexOf(' ')
            val head = if (firstSpace < 0) rulePart else rulePart.substring(0, firstSpace)
            val days: Set<DayOfWeek>
            val timePart: String
            if (DAY_SPEC.matches(head)) {
                days = parseDays(head) ?: return null
                timePart = if (firstSpace < 0) "" else rulePart.substring(firstSpace + 1).trim()
            } else {
                days = DayOfWeek.entries.toSet()
                timePart = rulePart
            }

            if (timePart.isEmpty()) return Rule(days, emptyList(), off = false)
            if (timePart == "off" || timePart == "closed") return Rule(days, emptyList(), off = true)

            val spans = timePart.split(",").map { spanText ->
                val m = TIME_SPAN.matchEntire(spanText.trim()) ?: return null
                val (h1, m1, h2, m2) = m.destructured
                val start = h1.toInt() * 60 + m1.toInt()
                var end = h2.toInt() * 60 + m2.toInt()
                if (start >= MINUTES_PER_DAY || end > MINUTES_PER_DAY || m1.toInt() > 59 || m2.toInt() > 59) return null
                if (end <= start) end += MINUTES_PER_DAY // overnight
                TimeSpan(start, end)
            }
            return Rule(days, spans, off = false)
        }

        private fun parseDays(spec: String): Set<DayOfWeek>? {
            val days = mutableSetOf<DayOfWeek>()
            for (group in spec.split(",")) {
                val ends = group.split("-")
                when (ends.size) {
                    1 -> days += DAY_NAMES[ends[0]] ?: return null
                    2 -> {
                        val from = DAY_NAMES[ends[0]] ?: return null
                        val to = DAY_NAMES[ends[1]] ?: return null
                        var d = from
                        while (true) {
                            days += d
                            if (d == to) break
                            d = d.plus(1L)
                        }
                    }
                    else -> return null
                }
            }
            return days
        }
    }
}
