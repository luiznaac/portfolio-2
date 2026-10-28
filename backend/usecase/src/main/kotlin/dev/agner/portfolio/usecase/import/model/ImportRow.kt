package dev.agner.portfolio.usecase.import.model

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A staged row as stored against its import. Same shape as the parser payload plus the row's id
// and its owning import. `date` is null only on an error row (see ImportRowPayload).
data class ImportRow(
    val id: Int,
    val importId: Int,
    val date: LocalDate?,
    val side: TradeSide,
    val venue: String,
    val ticker: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val institutionName: String,
    val error: String?,
)
