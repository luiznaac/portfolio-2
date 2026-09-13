package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.model.Order
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import dev.agner.portfolio.usecase.order.model.StrategyDelta
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Assembles the month's executable order list: per strategy per ticker, an ideal quantity (the
 * strategy's share of its asset class's ideal capital, times its latest edition's target weight
 * for that ticker) against the currently attributed quantity, netted per ticker across strategies
 * into one order, with same-ticker excess and shortage matched by [TransferMatcher] first so the
 * residual trade is as small as possible.
 *
 * Read-only: it never persists anything. [OrderPlanService] owns the proposal lifecycle that
 * decides what to do with the matches returned here.
 */
@Component
class OrderPlanAssembler(
    private val strategyIdealProvider: StrategyIdealProvider,
    private val attributionService: AttributionService,
    private val listedAssetRepository: IListedAssetRepository,
    private val quoteGateway: IQuoteGateway,
    private val transferMatcher: TransferMatcher,
    private val tradeLedger: TradeLedger,
) {

    suspend fun assemble(today: LocalDate): AssembledPlan {
        val strategyPlan = strategyIdealProvider.fetch()
        val strategyNames = strategyPlan.strategyNames
        val idealCapitalByStrategy = strategyPlan.idealCapitalByStrategy
        val targetsByStrategy = strategyPlan.targetsByStrategy
        val targetedTickers = targetsByStrategy.values.flatMap { it.keys }.toSet()

        // Every asset fetched once — the per-asset loop below would otherwise issue one query per
        // asset for the attribution replay.
        val allAssets = listedAssetRepository.fetchAll()
        val summaryByAsset = allAssets.associate { it.id to attributionService.summarize(it.id) }

        val relevant = allAssets.filter {
            it.ticker in targetedTickers || summaryByAsset.getValue(it.id).custodyQuantity.isZero().not()
        }

        val orders = mutableListOf<Order>()
        val matches = mutableListOf<PricedMatch>()

        for (asset in relevant) {
            val summary = summaryByAsset.getValue(asset.id)
            val quote = quoteGateway.getQuote(asset)
            val price = quote?.price

            val idealByStrategy = idealByStrategy(asset, summary, targetsByStrategy, idealCapitalByStrategy, price)
            val currentByStrategy = summary.balances.associate { it.strategyId to it.quantity }
            val deltaByStrategy = idealByStrategy.keys.associateWith {
                (currentByStrategy[it] ?: BigDecimal.ZERO) - (idealByStrategy[it] ?: BigDecimal.ZERO)
            }

            if (deltaByStrategy.values.any { !it.isZero() }) {
                matches += transferMatcher.match(asset.id, asset.ticker, deltaByStrategy)
                    .map { PricedMatch(it, price) }
            }

            val assessment = TickerAssessment(asset, summary, price, idealByStrategy, deltaByStrategy)
            buildOrder(assessment, strategyNames, today)?.let { orders += it }
        }

        return AssembledPlan(
            orders = orders,
            matches = matches,
            strategyNames = strategyNames,
            saleCeiling = tradeLedger.saleCeiling(today, orders, allAssets),
        )
    }

    private fun idealByStrategy(
        asset: ListedAsset,
        summary: AttributionSummary,
        targetsByStrategy: Map<Int, Map<String, BigDecimal>>,
        idealCapitalByStrategy: Map<Int, BigDecimal>,
        price: BigDecimal?,
    ): Map<Int, BigDecimal> {
        val holding = summary.balances.map { it.strategyId }
        val targeting = targetsByStrategy.filterValues { asset.ticker in it }.keys

        return (holding + targeting).toSet().associateWith { strategyId ->
            val targetWeight = targetsByStrategy[strategyId]?.get(asset.ticker) ?: BigDecimal.ZERO
            val idealCapital = idealCapitalByStrategy[strategyId] ?: BigDecimal.ZERO

            if (price == null || price <= BigDecimal.ZERO || targetWeight.isZero()) {
                BigDecimal.ZERO
            } else {
                (idealCapital * targetWeight).divide(price, 0, RoundingMode.DOWN)
            }
        }
    }

    private suspend fun buildOrder(
        assessment: TickerAssessment,
        strategyNames: Map<Int, String>,
        today: LocalDate,
    ): Order? {
        val totalIdeal = assessment.idealByStrategy.values.sumOf { it }
        val netDelta = assessment.summary.custodyQuantity - totalIdeal
        if (netDelta.isZero() || assessment.price == null) return null

        val kind = when {
            totalIdeal.isZero() && assessment.summary.custodyQuantity > BigDecimal.ZERO -> OrderKind.FULL_EXIT
            assessment.summary.custodyQuantity.isZero() && totalIdeal > BigDecimal.ZERO -> OrderKind.NEW_ENTRY
            netDelta > BigDecimal.ZERO -> OrderKind.SELL
            else -> OrderKind.BUY
        }
        val quantity = netDelta.abs()

        return Order(
            listedAssetId = assessment.asset.id,
            ticker = assessment.asset.ticker,
            isFii = assessment.asset.kind == AssetKind.FII,
            kind = kind,
            quantity = quantity,
            notional = (quantity * assessment.price).defaultScale(),
            contributions = assessment.deltaByStrategy.filterValues { !it.isZero() }.map {
                StrategyDelta(strategyId = it.key, strategyName = strategyNames[it.key].orEmpty(), delta = it.value)
            },
            dayTradeRisk = tradeLedger.hasOppositeTradeToday(assessment.asset.id, today, isSale = kind.isSale),
        )
    }
}

/** One [OrderPlanAssembler.assemble] result, before [OrderPlanService] reconciles the matches. */
data class AssembledPlan(
    val orders: List<Order>,
    val matches: List<PricedMatch>,
    val strategyNames: Map<Int, String>,
    val saleCeiling: SaleCeiling,
)

/** A [TransferMatch] paired with the quote price used to size it, kept for the auto-apply check. */
data class PricedMatch(val match: TransferMatch, val price: BigDecimal?)

/** Everything the order builder needs about one relevant ticker, grouped into a single context. */
private data class TickerAssessment(
    val asset: ListedAsset,
    val summary: AttributionSummary,
    val price: BigDecimal?,
    val idealByStrategy: Map<Int, BigDecimal>,
    val deltaByStrategy: Map<Int, BigDecimal>,
)
