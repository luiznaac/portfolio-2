package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.valueAt
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.StrategyHolding
import dev.agner.portfolio.usecase.strategy.model.StrategyHoldings
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

// The read behind the read-only manual Strategy detail: the Strategy's attributed slices, each valued
// at the current quote, with its share of the bucket total. The ledger is the source of the units —
// the same slices the Alocação tree shows — so the detail and the tree cannot disagree.
@Service
class StrategyHoldingsService(
    private val strategies: IStrategyRepository,
    private val attributions: IAttributionRepository,
    private val quotes: QuoteService,
) {

    private val hundred = BigDecimal("100")

    // Null means the Strategy does not exist — the controller maps that to 404.
    suspend fun fetch(strategyId: Int): StrategyHoldings? {
        val strategy = strategies.fetchById(strategyId) ?: return null
        val valued = attributions.fetchAll()
            .filter { it.strategyId == strategy.id }
            .mapAsync { slice ->
                StrategyHolding(
                    ticker = slice.ticker,
                    quantity = slice.quantity,
                    value = slice.quantity.valueAt(price = quotes.latestClose(slice.ticker)),
                    share = BigDecimal.ZERO,
                )
            }
            .awaitAll()
        val total = valued.sumOf { it.value }.defaultScale()

        return StrategyHoldings(
            strategyId = strategy.id,
            strategyName = strategy.name,
            totalValue = total,
            holdings = valued
                .map { holding -> holding.copy(share = shareOf(holding.value, total)) }
                .sortedWith(compareByDescending<StrategyHolding> { it.value }.thenBy { it.ticker }),
        )
    }

    private fun shareOf(value: BigDecimal, total: BigDecimal): BigDecimal =
        if (total.signum() == 0) {
            BigDecimal.ZERO
        } else {
            value.multiply(hundred).divide(total, 2, RoundingMode.HALF_UP)
        }
}
