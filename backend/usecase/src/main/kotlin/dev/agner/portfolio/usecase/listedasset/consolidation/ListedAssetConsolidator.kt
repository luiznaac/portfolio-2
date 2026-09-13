package dev.agner.portfolio.usecase.listedasset.consolidation

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.consolidation.ProductConsolidator
import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.listedasset.consolidation.model.ListedAssetConsolidationContext
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.FII
import dev.agner.portfolio.usecase.listedasset.position.model.ListedAssetPosition
import dev.agner.portfolio.usecase.listedasset.position.repository.IListedAssetPositionRepository
import dev.agner.portfolio.usecase.tax.TaxRules
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

/**
 * Consolidates one listed asset the same way BondConsolidator/CheckingAccountConsolidator do:
 * replay the ledger (here, [AveragePriceCalculator] over trades + corporate actions) into a
 * current position, price it, and persist a [ListedAssetPosition] the frontend charts unchanged.
 *
 * The `taxes` estimate here is deliberately naive — an estimated swing-sale income tax on the
 * unrealized gain (what a normal sale of the open position today would cost), ignoring the monthly
 * stock exemption and loss carry-forward. Real tax planning lives in usecase/tax; this is just
 * enough to show what redeeming today would roughly cost, the same spirit as the bond
 * IOF/income-tax estimate.
 */
@Component
class ListedAssetConsolidator(
    private val contextProvider: ListedAssetConsolidationContextProvider,
    private val quoteGateway: IQuoteGateway,
    private val calculator: AveragePriceCalculator,
    private val positionRepository: IListedAssetPositionRepository,
    private val clock: Clock,
) : ProductConsolidator<ListedAssetConsolidationContext> {

    override val type = ProductType.LISTED_ASSET

    override suspend fun getConsolidatableIds() = contextProvider.fetchConsolidatableIds()

    override suspend fun buildContext(productId: Int) = contextProvider.buildContext(productId)

    override suspend fun consolidate(ctx: ListedAssetConsolidationContext) {
        val result = calculator.calculate(ctx.trades, ctx.corporateActions)

        if (result.position.quantity <= BigDecimal.ZERO) {
            return
        }

        val quote = quoteGateway.getQuote(ctx.asset)
            ?: error("No quote available for ${ctx.asset.ticker}, cannot consolidate")

        val marketValue = (result.position.quantity * quote.price).defaultScale()
        val unrealizedGain = marketValue - result.position.totalCost
        val taxRate = if (ctx.asset.kind == FII) {
            TaxRules.FII_CAPITAL_GAINS_RATE
        } else {
            TaxRules.STOCK_CAPITAL_GAINS_RATE
        }
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
