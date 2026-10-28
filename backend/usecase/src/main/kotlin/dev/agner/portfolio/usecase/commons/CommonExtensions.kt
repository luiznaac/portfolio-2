package dev.agner.portfolio.usecase.commons

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import kotlin.collections.ArrayList
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun <T, R> Iterable<T>.mapToSet(transformer: (T) -> R) = map(transformer).toSet()

suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R) = coroutineScope {
    map { async { transform(it) } }
}

suspend fun <T, R> Iterable<Deferred<T>>.mapAsyncDeferred(transform: suspend (T) -> R) = coroutineScope {
    map {
        async {
            transform(it.await())
        }
    }
}

suspend fun <T> Iterable<Deferred<T>>.onEachAsyncDeferred(transform: suspend (T) -> Unit) = coroutineScope {
    map {
        async {
            val itt = it.await()
            transform(it.await())
            itt
        }
    }
}

inline fun <T, R> Iterable<T>.foldUntil(initial: R, condition: R.() -> Boolean, operation: (acc: R, T) -> R): R {
    var accumulator = initial
    for (element in this) {
        accumulator = operation(accumulator, element)
        if (condition(accumulator)) break
    }
    return accumulator
}

inline fun <reified R> Iterable<*>.firstOfInstance(): R {
    return filterIsInstanceTo(ArrayList<R>()).firstOrNull()
        ?: throw NoSuchElementException("Collection contains no element of type ${R::class.simpleName}")
}

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

// The nearest weekday on either side. The app has no exchange-holiday calendar, so "business day"
// means "weekday"; the caller states the holiday caveat where the date surfaces.
fun LocalDate.nextWeekday(): LocalDate {
    var day = nextDay()
    while (day.isWeekend()) day = day.nextDay()
    return day
}

fun LocalDate.weekdayOnOrBefore(): LocalDate {
    var day = this
    while (day.isWeekend()) day = day.dayBefore()
    return day
}

fun LocalDateRange.removeWeekends() = mapNotNull { it.takeIf { !it.isWeekend() } }

fun BigDecimal.defaultScale() = setScale(2, RoundingMode.HALF_EVEN)

// A quantity valued at a price, treating a missing quote as zero — the rule the allocation tree and
// the manual Strategy holdings read share.
fun BigDecimal.valueAt(price: BigDecimal?): BigDecimal =
    if (price == null) BigDecimal.ZERO else (this * price).defaultScale()

private val HUNDRED = BigDecimal("100")

// A percentage rate (15 for 15%) applied to an amount, at the repo's money scale.
fun BigDecimal.percentOf(rate: BigDecimal) = multiply(rate).divide(HUNDRED).defaultScale()

// A level's Deficit — its Ideal minus its Actual (CONTEXT.md); the negative side is the Surplus.
fun BigDecimal.deficitAgainst(actual: BigDecimal) = (this - actual).defaultScale()

// BigDecimal.equals() is scale-sensitive (0 != 0.00000000, even though compareTo() says they're
// equal) — comparing a computed value against BigDecimal.ZERO with == silently misses "zero at a
// different scale" and, worse, can loop forever on a decrement that never satisfies ==. Use this
// instead of `== BigDecimal.ZERO` / `!= BigDecimal.ZERO` anywhere the value's scale isn't fixed.
fun BigDecimal.isZero() = signum() == 0
