package dev.agner.portfolio.usecase.catalog.model

import kotlinx.datetime.LocalDate

// The one combined catalog-and-quotes refresh result. `catalog` and `quotes` are the rows written,
// `asOf` the date of the daily close that was used, and the two lists explain what did not complete:
// `failed` an outage (a source or a ticker), `unclassified` the held tickers that still have no class
// and why. A partial failure is still a 200 with `failed` populated.
data class TickerRefreshResult(
    val catalog: Int,
    val quotes: Int,
    val asOf: LocalDate?,
    val failed: List<TickerRefreshFailure>,
    val unclassified: List<UnclassifiedTicker>,
)

enum class RefreshFailureKind {
    SOURCE,
    TICKER,
}

sealed class TickerRefreshFailure(
    val kind: RefreshFailureKind,
    open val reason: String,
) {
    // A whole feed failed: the daily file, or one annual file. `source` names which.
    data class Source(
        val source: String,
        override val reason: String,
    ) : TickerRefreshFailure(RefreshFailureKind.SOURCE, reason)

    // One ledger ticker came out of the refresh without a usable close.
    data class Ticker(
        val ticker: String,
        override val reason: String,
    ) : TickerRefreshFailure(RefreshFailureKind.TICKER, reason)
}

// A held ticker that is still classless. `ABSENT_FROM_LATEST_FILE` is a paper the refreshed file no
// longer lists (delisted, or the file could not be read); `UNHANDLED_BDI_CODE` is a listed paper
// whose BDI the catalog does not map yet. Neither is guessed into a class.
data class UnclassifiedTicker(
    val ticker: String,
    val reason: UnclassifiedReason,
)

enum class UnclassifiedReason {
    ABSENT_FROM_LATEST_FILE,
    UNHANDLED_BDI_CODE,
}
