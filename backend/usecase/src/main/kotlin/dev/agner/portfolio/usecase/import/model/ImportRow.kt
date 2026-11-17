package dev.agner.portfolio.usecase.import.model

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A staged row as stored against its import. Same shape as the parser payload plus the row's id
// and its owning import. `date` is null only on an error row (see ImportRowPayload). `situation` is
// the review's read-time verdict: it stays null until `ImportService.fetchRows` computes it, so a
// stored row never claims a verdict it was not given. `outcome` is what apply decided and is
// persisted, so a rejected row stays visible as the retry batch after the import is APPLIED.
data class ImportRow(
    val id: Int,
    val importId: Int,
    val date: LocalDate?,
    val side: TradeSide,
    val venue: String,
    val ticker: String?,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val institutionName: String,
    val error: String?,
    val situation: ImportRowSituation? = null,
    val outcome: ImportRowOutcome? = null,
    // A broker note's raw asset description and the note it came from; null for a layout that
    // prints a ticker. The review shows the description beside the ticker the alias resolved.
    val rawDescription: String? = null,
    val noteNumber: String? = null,
)
