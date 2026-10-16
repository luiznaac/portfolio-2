package dev.agner.portfolio.usecase.import.model

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One row a parser produced from a broker file, before it is persisted. `error` marks a row-level
// problem (a missing/unparseable date, a value mismatch, an unknown code): the row still stages so
// it can be edited or dropped in review, and apply skips it. Row-level errors never block the file.
// `date` is null only on an error row, which is why a trade is created only from an error-free row.
data class ImportRowPayload(
    val date: LocalDate?,
    val side: TradeSide,
    val venue: String,
    val ticker: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val institutionName: String,
    val error: String? = null,
)
