package dev.agner.portfolio.usecase.extension

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun <T, R> Iterable<T>.mapToSet(transformer: (T) -> R) = map(transformer).toSet()

suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R) = coroutineScope {
    map { async { transform(it) } }
}

inline fun <T, R> Iterable<T>.foldUntil(initial: R, condition: R.() -> Boolean, operation: (acc: R, T) -> R): R {
    var accumulator = initial
    for (element in this) {
        accumulator = operation(accumulator, element)
        if (condition(accumulator)) break
    }
    return accumulator
}

@OptIn(ExperimentalTime::class)
fun LocalDateTime.Companion.now(clock: Clock) = clock.instant().toKotlinInstant().toLocalDateTime(TimeZone.UTC)

fun LocalDate.Companion.today(clock: Clock) = LocalDateTime.now(clock).date

fun LocalDate.Companion.yesterday(clock: Clock) = LocalDate.today(clock).minus(1, DateTimeUnit.DAY)

fun LocalDate.nextDay() = plus(1, DateTimeUnit.DAY)
