package dev.agner.portfolio.usecase.tax.stepup

import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.tax.stepup.model.StepUpPlan
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.model.Trade
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
        val remainingCeiling = orderPlanService.computePlan().saleCeiling.remaining

        val candidates = listedAssetRepository.fetchAll()
            .filter { it.kind != AssetKind.FII }
            .mapNotNull { asset ->
                val trades = tradeRepository.fetchByAssetId(asset.id)
                val boughtToday = trades.any { it.date == today && it is Trade.Buy }
                if (boughtToday) return@mapNotNull null

                val corporateActions = corporateActionRepository.fetchByAssetId(asset.id)
                val position = averagePriceCalculator.calculate(trades, corporateActions).position
                if (position.quantity <= BigDecimal.ZERO) return@mapNotNull null

                val quote = quoteGateway.getQuote(asset) ?: return@mapNotNull null

                StepUpCandidate(
                    listedAssetId = asset.id,
                    ticker = asset.ticker,
                    quantity = position.quantity,
                    averagePrice = position.averagePrice,
                    currentPrice = quote.price,
                )
            }

        return planner.plan(candidates, remainingCeiling, today)
    }
}
