package dev.agner.portfolio.usecase.tax.stepup

import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Builds the candidates [StepUpPlanner] ranks: one per [AssetKind.STOCK] position still open after
 * netting out the sells the order plan already proposes, skipping anything bought today (selling it
 * would be a day trade) and any ticker without a quote. Non-stocks are excluded — only stocks have
 * the R$20k monthly sale exemption to maximize; FIIs, ETFs and BDRs are always taxed.
 */
@Component
class StepUpCandidateProvider(
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeRepository: ITradeRepository,
    private val corporateActionRepository: ICorporateActionRepository,
    private val quoteGateway: IQuoteGateway,
    private val averagePriceCalculator: AveragePriceCalculator,
) {

    suspend fun build(today: LocalDate, plannedSells: Map<Int, BigDecimal>): List<StepUpCandidate> =
        listedAssetRepository.fetchAll()
            .filter { it.kind == AssetKind.STOCK }
            .mapNotNull { asset ->
                val trades = tradeRepository.fetchByAssetId(asset.id)
                val boughtToday = trades.any { it.date == today && it is Trade.Buy }
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
}
