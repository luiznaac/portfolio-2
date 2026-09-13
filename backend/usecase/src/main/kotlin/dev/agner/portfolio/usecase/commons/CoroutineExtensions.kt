package dev.agner.portfolio.usecase.commons

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
