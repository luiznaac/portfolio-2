package dev.agner.portfolio.usecase.commons

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.collections.ArrayList

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun <T, R> Iterable<T>.mapToSet(transformer: (T) -> R) = map(transformer).toSet()

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

fun BigDecimal.defaultScale() = setScale(2, RoundingMode.HALF_EVEN)

// BigDecimal.equals() is scale-sensitive (0 != 0.00000000, even though compareTo() says they're
// equal) — comparing a computed value against BigDecimal.ZERO with == silently misses "zero at a
// different scale" and, worse, can loop forever on a decrement that never satisfies ==. Use this
// instead of `== BigDecimal.ZERO` / `!= BigDecimal.ZERO` anywhere the value's scale isn't fixed.
fun BigDecimal.isZero() = signum() == 0
