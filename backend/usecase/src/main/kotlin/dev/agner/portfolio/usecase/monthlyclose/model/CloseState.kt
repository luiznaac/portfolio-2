package dev.agner.portfolio.usecase.monthlyclose.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.TransferProposal
import dev.agner.portfolio.usecase.order.model.UnplannedTrade
import dev.agner.portfolio.usecase.tax.model.CarryBucket
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Everything a closed month ended with, frozen as one document. The snapshot is the historical
// evolution's source of truth: holdings and their fiscal basis, the attribution ledger, the class
// totals, the month's Capital/Floor/Caixa, its income, its orders and its tax close.
data class CloseState(
    val period: YearMonth,
    val capital: BigDecimal,
    val floor: BigDecimal,
    // The Uninvested Balance: Capital minus the value of every position (CONTEXT.md -> Caixa).
    val caixa: BigDecimal,
    val holdings: List<HoldingSnapshot>,
    val attributions: List<AttributionSnapshot>,
    val classes: List<ClassTotalSnapshot>,
    val income: IncomeTotals,
    val orders: OrdersSnapshot,
    val tax: TaxCloseSnapshot,
)

// A held listed asset at the close: its custody quantity and fiscal average price.
data class HoldingSnapshot(
    val ticker: String,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
)

// One attribution slice at the close. The strategy's name rides beside its id so the snapshot reads
// as history even if the catalog is renamed later.
data class AttributionSnapshot(
    val ticker: String,
    val strategyId: Int,
    val strategy: String,
    val quantity: BigDecimal,
)

// The class rung's totals — the aggregation the historical series is built from.
data class ClassTotalSnapshot(
    val assetClass: AssetClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    val unattributed: BigDecimal,
)

// The month's income totals, summed across the income movements dated inside it regardless of
// lifecycle stage: a payment due in the month counts as that month's income even if it has not been
// reconciled yet.
data class IncomeTotals(
    val gross: BigDecimal,
    val retained: BigDecimal,
    val net: BigDecimal,
)

// The order lifecycle as it stood when the month closed: the persisted suggestions, the transfer
// inbox (all decided at a successful close) and the trades no suggestion planned.
data class OrdersSnapshot(
    val orders: List<SuggestedOrder>,
    val transfers: List<TransferProposal>,
    val unplannedTrades: List<UnplannedTrade>,
)

// The tax state frozen at the close: each carry bucket's roll and the exemption meter's final values.
data class TaxCloseSnapshot(
    val carry: List<CarryBucket>,
    val meter: ExemptionMeter,
)
