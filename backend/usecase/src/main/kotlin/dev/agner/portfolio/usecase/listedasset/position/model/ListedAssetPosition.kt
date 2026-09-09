package dev.agner.portfolio.usecase.listedasset.position.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * Same shape as BondPosition/CheckingAccountPosition (date, principal, yield, taxes) — this is
 * the shared `Position` shape the frontend already knows how to chart, so a listed asset slots
 * into the existing dashboard and PositionChart with no new frontend code.
 *
 * For a listed asset: principal = cost basis, yield = unrealized gain (mark-to-market minus cost),
 * taxes = a same-day-sale income-tax estimate. See [dev.agner.portfolio.usecase.listedasset.consolidation.ListedAssetConsolidator].
 */
data class ListedAssetPosition(
    val date: LocalDate,
    val principal: BigDecimal,
    val yield: BigDecimal,
    val taxes: BigDecimal,
)
