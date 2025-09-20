package dev.agner.portfolio.usecase.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

inline fun <T, R> Iterable<T>.mapToSet(transformer: (T) -> R) = map(transformer).toSet()

suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R) = coroutineScope {
    map { async { transform(it) } }
}
