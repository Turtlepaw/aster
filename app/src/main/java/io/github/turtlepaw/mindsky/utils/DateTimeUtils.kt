package io.github.turtlepaw.mindsky.utils

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
