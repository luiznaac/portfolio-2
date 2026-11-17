package dev.agner.portfolio.usecase.corporateaction.gateway

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// The brapi window a corporate-action sync asks for: the earliest ledger trade through today.
data class BrapiWindow(
    val from: LocalDate,
    val to: LocalDate,
)

// One ticker rename as brapi reports it. `canonicalSymbol` is the latest code in the chain, so a
// paper renamed twice still resolves in one hop; `conversionRatio` is present only when the rename
// also converted the shares.
data class BrapiRename(
    val oldSymbol: String,
    val newSymbol: String,
    val canonicalSymbol: String,
    val effectiveDate: LocalDate,
    val conversionRatio: BigDecimal? = null,
)

// One cash payment as brapi reports it, on either the stock or the FII endpoint: a stock dividend
// or JCP, or a FII rendimento/amortização. `rate` is on the split-adjusted price scale, while
// `rawRate` (present on the Pro plan) is the amount actually paid per share.
data class BrapiIncome(
    val symbol: String,
    val label: String,
    val rate: BigDecimal? = null,
    val rawRate: BigDecimal? = null,
    val exDate: LocalDate? = null,
    val lastDatePrior: LocalDate? = null,
    val paymentDate: LocalDate? = null,
)

// One stock event (desdobramento, grupamento, bonificação) as brapi reports it. `factor` is stated
// the way the exchange states it: new shares per old share, so a 2:1 split is 2 and a 100:1 reverse
// split is 0.01.
data class BrapiStockAction(
    val symbol: String,
    val label: String,
    val factor: BigDecimal? = null,
    val exDate: LocalDate? = null,
    val approvedOn: LocalDate? = null,
)

// One stock ticker's two event families, which the same brapi endpoint returns together.
data class BrapiStockEvents(
    val cashDividends: List<BrapiIncome> = emptyList(),
    val stockDividends: List<BrapiStockAction> = emptyList(),
)

// One endpoint's parsed answer beside the raw body it came from. The body is persisted with the
// sync audit so a later parser change never needs another paid month.
data class BrapiFetch<T>(
    val payload: String,
    val items: T,
)

// The brapi corporate-action feed. Renames are the free endpoint; stock and FII events need the Pro
// plan and the token. The sync batches the tickers and treats a non-credential failure as that
// batch's failure, so the caller owns the window and the retry.
interface IBrapiCorporateActionSource {

    suspend fun fetchRenames(tickers: List<String>): BrapiFetch<List<BrapiRename>>

    suspend fun fetchStockEvents(tickers: List<String>, window: BrapiWindow): BrapiFetch<BrapiStockEvents>

    suspend fun fetchFundEvents(tickers: List<String>, window: BrapiWindow): BrapiFetch<List<BrapiIncome>>
}
