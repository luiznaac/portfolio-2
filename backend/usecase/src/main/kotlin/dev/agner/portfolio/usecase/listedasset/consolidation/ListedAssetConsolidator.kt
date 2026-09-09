package dev.agner.portfolio.usecase.listedasset.consolidation

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.consolidation.ProductConsolidator
import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.listedasset.ListedAssetService
import dev.agner.portfolio.usecase.listedasset.consolidation.model.ListedAssetConsolidationContext
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.FII
import dev.agner.portfolio.usecase.listedasset.position.model.ListedAssetPosition
import dev.agner.portfolio.usecase.listedasset.position.repository.IListedAssetPositionRepository
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.TradeService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

/**
 * Consolidates one listed asset the same way BondConsolidator/CheckingAccountConsolidator do:
 * replay the ledger (here, [AveragePriceCalculator] over trades + corporate actions) into a
 * current position, price it, and persist a [ListedAssetPosition] the frontend charts unchanged.
 *
 * The `taxes` estimate here is deliberately naive — same-day-sale income tax on the unrealized
 * gain, ignoring the monthly R$20k stock exemption and loss carry-forward. Real tax planning
 * (StepUpPlanner) is Fase 5 of the plan; this is just enough to show "what redeeming today would
 * roughly cost", same spirit as Bond's IOF/Renda estimate.
 */
@Component
class ListedAssetConsolidator(
    private val listedAssetService: ListedAssetService,
    private val tradeService: TradeService,
    private val corporateActionService: CorporateActionService,
    private val quoteGateway: IQuoteGateway,
    private val calculator: AveragePriceCalculator,
    private val positionRepository: IListedAssetPositionRepository,
    private val clock: Clock,
) : ProductConsolidator<ListedAssetConsolidationContext> {

    override val type = ProductType.LISTED_ASSET

    override suspend fun getConsolidatableIds() = listedAssetService.fetchAll().map { it.id }

    override suspend fun buildContext(productId: Int): ListedAssetConsolidationContext {
        val asset = listedAssetService.fetchById(productId)

        return ListedAssetConsolidationContext(
            asset = asset,
            trades = tradeService.fetchByAssetId(productId),
            corporateActions = corporateActionService.fetchByAssetId(productId),
        )
    }

    override suspend fun consolidate(ctx: ListedAssetConsolidationContext) {
        val result = calculator.calculate(ctx.trades, ctx.corporateActions)

        if (result.position.quantity <= BigDecimal.ZERO) {
            return
        }

        val quote = quoteGateway.getQuote(ctx.asset)
            ?: error("No quote available for ${ctx.asset.ticker}, cannot consolidate")

        val marketValue = (result.position.quantity * quote.price).defaultScale()
        val unrealizedGain = marketValue - result.position.totalCost
        val taxRate = if (ctx.asset.kind == FII) BigDecimal("0.15") else BigDecimal("0.20")
        val estimatedTax = if (unrealizedGain > BigDecimal.ZERO) {
            (unrealizedGain * taxRate).defaultScale()
        } else {
            BigDecimal("0.00")
        }

        positionRepository.save(
            ctx.asset.id,
            ListedAssetPosition(
                date = LocalDate.today(clock),
                principal = result.position.totalCost.defaultScale(),
                yield = unrealizedGain,
                taxes = estimatedTax,
            ),
        )
    }
}
