package dev.agner.portfolio.usecase.import.model

// The owner's answer for a broker description the review could not resolve: the ticker the
// description names. Confirming writes the institution's Broker Asset Alias and updates the row.
data class ImportRowTickerConfirmation(
    val ticker: String,
)
