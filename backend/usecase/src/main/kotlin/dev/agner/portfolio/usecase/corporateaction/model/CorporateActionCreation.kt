package dev.agner.portfolio.usecase.corporateaction.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * What the client sends to register a corporate action. The owning asset is *not* part of this
 * shape — it comes from the URL path and is passed alongside it to
 * [dev.agner.portfolio.usecase.corporateaction.CorporateActionService.create].
 */
sealed class CorporateActionCreation {
    abstract val date: LocalDate

    data class SplitCreation(
        override val date: LocalDate,
        val ratio: BigDecimal,
    ) : CorporateActionCreation()

    data class ReverseSplitCreation(
        override val date: LocalDate,
        val ratio: BigDecimal,
    ) : CorporateActionCreation()

    data class BonusCreation(
        override val date: LocalDate,
        val ratio: BigDecimal,
        val valuePerNewShare: BigDecimal,
    ) : CorporateActionCreation()

    data class TickerChangeCreation(
        override val date: LocalDate,
        val newTicker: String,
    ) : CorporateActionCreation()
}
