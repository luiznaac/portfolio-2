package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.DomainException

// Raised when a held ticker has no stored close: sizing new entries against an unknown Actual would
// invent a book value the app has explicitly refused to invent. The full ticker list rides along so
// the caller can point the owner at each quote to refresh.
class UnpricedPositionsException(
    val tickers: List<String>,
) : DomainException(
    error = "unpriced-positions",
    userMessage = "The plan needs all ${tickers.size} held tickers priced: ${preview(tickers)}",
    detail = "Plan blocked by ${tickers.size} unpriced position(s): ${tickers.joinToString()}",
)

// Raised when a held ticker has no effective class: placement, attribution and tax all key off the
// class, so an order plan built on an unknown one has no basis. A refresh or a manual classification
// clears it.
class UnclassifiedPositionsException(
    val tickers: List<String>,
) : DomainException(
    error = "unclassified-positions",
    userMessage = "The plan needs all ${tickers.size} held tickers classified: ${preview(tickers)}",
    detail = "Plan blocked by ${tickers.size} unclassified position(s): ${tickers.joinToString()}",
)

// The message states the count and this names the first few tickers; the detail carries the rest.
private fun preview(tickers: List<String>) = tickers.take(PREVIEW_LIMIT).joinToString()

private const val PREVIEW_LIMIT = 5
