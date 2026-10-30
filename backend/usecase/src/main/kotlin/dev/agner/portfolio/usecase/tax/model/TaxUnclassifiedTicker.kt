package dev.agner.portfolio.usecase.tax.model

// Why an unclassified ticker is on the report: it is held at the period's end, sold inside it, or
// both. One status, not two booleans, so "neither" cannot be represented.
enum class TaxUnclassifiedStatus {
    HELD,
    SOLD,
    HELD_AND_SOLD,
}

// A ticker the catalog could not classify, held at the period's end or sold inside it. Its sales are
// excluded from the meter rather than guessed; the report flags it so the owner can classify it.
data class TaxUnclassifiedTicker(
    val ticker: String,
    val status: TaxUnclassifiedStatus,
)
