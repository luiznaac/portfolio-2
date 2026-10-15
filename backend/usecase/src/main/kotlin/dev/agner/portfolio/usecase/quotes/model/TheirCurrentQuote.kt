package dev.agner.portfolio.usecase.quotes.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A current quote as a provider (brapi) returns it, before it is stored. `date` is the provider's
// quote timestamp, resolved to the trading day it belongs to.
data class TheirCurrentQuote(
    val ticker: String,
    val date: LocalDate,
    val close: BigDecimal,
)
