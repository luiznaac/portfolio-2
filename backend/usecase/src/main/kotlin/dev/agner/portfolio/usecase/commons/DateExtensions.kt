package dev.agner.portfolio.usecase.commons

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateRange
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

@OptIn(ExperimentalTime::class)
fun LocalDateTime.Companion.now(clock: Clock) =
    clock.instant().toKotlinInstant().toLocalDateTime(clock.zone.toKotlinTimeZone())

fun LocalDate.Companion.today(clock: Clock) = LocalDateTime.now(clock).date

fun LocalDate.Companion.yesterday(clock: Clock) = LocalDate.today(clock).minus(1, DateTimeUnit.DAY)

fun LocalDate.nextDay() = plus(1, DateTimeUnit.DAY)

fun LocalDate.dayBefore() = minus(1, DateTimeUnit.DAY)

fun LocalDate.isWeekend() = listOf(SATURDAY, SUNDAY).contains(dayOfWeek)

fun LocalDate.toMondayIfWeekend() = when (dayOfWeek) {
    SATURDAY -> nextDay().nextDay()
    SUNDAY -> nextDay()
    else -> this
}

fun LocalDateRange.removeWeekends() = mapNotNull { it.takeIf { !it.isWeekend() } }
