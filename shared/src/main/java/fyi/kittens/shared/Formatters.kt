package fyi.kittens.shared

import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(kotlin.time.ExperimentalTime::class)
object Formatters {
    fun Instant.toRelativeTimeString(
        clock: Clock = Clock.System,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val now = clock.now().toJavaInstant().atZone(zoneId)
        val then = this.toJavaInstant().atZone(zoneId)

        val years = ChronoUnit.YEARS.between(then, now)
        if (years > 0) return "${years}y"

        val months = ChronoUnit.MONTHS.between(then, now)
        if (months > 0) return "${months}mo"

        val days = ChronoUnit.DAYS.between(then, now)
        if (days > 0) return "${days}d"

        val hours = ChronoUnit.HOURS.between(then, now)
        if (hours > 0) return "${hours}h"

        val minutes = ChronoUnit.MINUTES.between(then, now)
        if (minutes > 0) return "${minutes}m"

        return "now"
    }

    fun formatNumberForLocale(number: Int): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        return numberFormat.format(number)
    }

    fun formatCompactNumber(number: Int): String {
        return when {
            number >= 1_000_000_000 -> formatWithSuffix(number, 1_000_000_000, "B")
            number >= 1_000_000 -> formatWithSuffix(number, 1_000_000, "M")
            number >= 1_000 -> formatWithSuffix(number, 1_000, "K")
            else -> number.toString()
        }
    }

    private fun formatWithSuffix(number: Int, divisor: Int, suffix: String): String {
        val value = number.toDouble() / divisor
        val formatted = DecimalFormat("#.#").format(value) // 1 decimal place max
        return "$formatted$suffix"
    }
}