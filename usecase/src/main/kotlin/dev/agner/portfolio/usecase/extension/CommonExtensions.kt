package dev.agner.portfolio.usecase.extension

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun <T, R> Iterable<T>.mapToSet(transformer: (T) -> R) = map(transformer).toSet()

suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R) = coroutineScope {
    map { async { transform(it) } }
}

@OptIn(ExperimentalTime::class)
fun LocalDateTime.Companion.now() = Clock.System.now().toLocalDateTime(TimeZone.UTC)

fun LocalDate.Companion.now() = LocalDateTime.now().date
