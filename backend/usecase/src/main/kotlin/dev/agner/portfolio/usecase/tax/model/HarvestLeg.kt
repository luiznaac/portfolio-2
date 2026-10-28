package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One side of a harvest pair. `amount` is quantity × price: the gross proceeds of the SELL and the
// cash the REBUY spends. Both legs are editable before acceptance, so the pair keeps them as data
// rather than as a computation.
data class HarvestLeg(
    val date: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val amount: BigDecimal,
)
