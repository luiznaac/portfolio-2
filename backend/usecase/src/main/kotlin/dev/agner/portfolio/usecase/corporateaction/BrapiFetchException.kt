package dev.agner.portfolio.usecase.corporateaction

// One batch's failure that does not invalidate the run: a timeout, a 429, a 5xx, an unreadable
// payload, or a payload the provider refused for that ticker set. The sync records the batch's
// tickers and keeps the successful ones.
class BrapiFetchException(
    val reason: String,
    cause: Throwable? = null,
) : RuntimeException(reason, cause)
