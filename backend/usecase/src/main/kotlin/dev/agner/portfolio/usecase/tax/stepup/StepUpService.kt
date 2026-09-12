package dev.agner.portfolio.usecase.tax.stepup

import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.tax.stepup.model.StepUpPlan
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

/**
 * Orchestrates [StepUpPlanner]: builds one candidate per stock position with an unrealized gain,
 * excluding FIIs (no exemption to maximize) and anything already bought today (selling it would
 * be a day trade — same risk [dev.agner.portfolio.usecase.order.OrderPlanService] flags), and
 * feeds it the month's remaining sale-exemption ceiling.
 *
 * The order plan's own pending sells are netted out first: a ticker the plan already sells only
 * enters with the quantity the plan does not cover, so the two lists never propose the same shares
 * twice (see [plan]).
 */
@Service
class StepUpService(
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeRepository: ITradeRepository,
    private val corporateActionRepository: ICorporateActionRepository,
    private val quoteGateway: IQuoteGateway,
    private val averagePriceCalculator: AveragePriceCalculator,
    private val orderPlanService: OrderPlanService,
    private val planner: StepUpPlanner,
    private val clock: Clock,
) {

    suspend fun plan(): StepUpPlan {
        val today = LocalDate.today(clock)
        val orderPlan = orderPlanService.computePlan()
        val plannedSells = orderPlan.orders
            .filter { it.kind == OrderKind.SELL || it.kind == OrderKind.FULL_EXIT }
            .associate { it.listedAssetId to it.quantity }

        val candidates = listedAssetRepository.fetchAll()
            .filter { it.kind != AssetKind.FII }
            .mapNotNull { asset ->
                val trades = tradeRepository.fetchByAssetId(asset.id)
                val boughtToday = trades.any { it.date == today && it.quantity > BigDecimal.ZERO }
                if (boughtToday) return@mapNotNull null

                val corporateActions = corporateActionRepository.fetchByAssetId(asset.id)
                val position = averagePriceCalculator.calculate(trades, corporateActions).position
                val planned = plannedSells[asset.id] ?: BigDecimal.ZERO
                val quantity = position.quantity - planned
                if (quantity <= BigDecimal.ZERO) return@mapNotNull null

                val quote = quoteGateway.getQuote(asset) ?: return@mapNotNull null

                StepUpCandidate(
                    listedAssetId = asset.id,
                    ticker = asset.ticker,
                    quantity = quantity,
                    averagePrice = position.averagePrice,
                    currentPrice = quote.price,
                )
            }

        return planner.plan(candidates, orderPlan.saleCeiling.remaining, today)
    }
}
