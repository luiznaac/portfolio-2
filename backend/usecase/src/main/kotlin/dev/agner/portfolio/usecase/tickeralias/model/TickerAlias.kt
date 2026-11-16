package dev.agner.portfolio.usecase.tickeralias.model

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One edge of a ticker's identity chain: the code an asset was traded under and the code that
// replaced it on `effectiveDate`. A chain (A -> B -> C) is a set of these edges; the resolver follows
// it to the latest canonical code, so a buy under A and a sell under C are one paper. `ratio` is the
// conversion factor the rename carried, when it had one, and `corporateActionId` links the edge back
// to the TICKER_CHANGE event that wrote it.
data class TickerAlias(
    val oldTicker: String,
    val canonicalTicker: String,
    val effectiveDate: LocalDate,
    val source: CorporateActionSource,
    val ratio: BigDecimal? = null,
    val corporateActionId: Int? = null,
)
