package dev.agner.portfolio.usecase.income.model

import dev.agner.portfolio.usecase.listedasset.model.DividendType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * A [dev.agner.portfolio.usecase.listedasset.model.DividendDeclaration] turned into money for
 * *your* position: gross value at the quantity actually held on the ex-date, minus the
 * withholding B3 doesn't apply for you. This is the expected amount, not the received one — see
 * [IncomeReconciliation] for the two compared.
 */
data class IncomeEvent(
    val listedAssetId: Int,
    val ticker: String,
    val type: DividendType,
    val exDate: LocalDate,
    val paymentDate: LocalDate?,
    val quantityHeld: BigDecimal,
    val grossAmount: BigDecimal,
    val retainedTax: BigDecimal,
    val netAmount: BigDecimal,
)

/** Yield on cost for one asset: net income received to date divided by its total cost basis. */
data class AssetIncomeSummary(
    val listedAssetId: Int,
    val ticker: String,
    val totalNet: BigDecimal,
    val costBasis: BigDecimal,
    val yieldOnCost: BigDecimal,
)
