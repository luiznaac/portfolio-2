package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Sell
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.consolidation.ProductConsolidator
import dev.agner.portfolio.usecase.consolidation.ProductType
import org.springframework.stereotype.Component

@Component
class BondConsolidator(
    private val repository: IBondOrderStatementRepository,
    private val bondOrderService: BondOrderService,
    private val bondConsolidationService: BondConsolidationService,
) : ProductConsolidator<BondConsolidationContext> {

    override val type = ProductType.BOND

    override suspend fun buildContext(productId: Int): BondConsolidationContext {
        val orders = bondOrderService.fetchByBondId(productId)
        val alreadyConsolidatedSells = repository.fetchAlreadyConsolidatedSellIdsByOrderId(productId)
        val sells = orders
            .filterIsInstance<Sell>()
            .filterNot { alreadyConsolidatedSells.contains(it.id) }

        val alreadyRedeemedBuys = repository.fetchAlreadyRedeemedBuyIdsByOrderId(productId)
        val buys = orders
            .filterIsInstance<Buy>()
            .filterNot { alreadyRedeemedBuys.contains(it.id) }
            .sortedBy { it.date }

        val fullRedemption = orders.filterIsInstance<FullRedemption>().firstOrNull()

        return BondConsolidationContext(buys, sells, fullRedemption)
    }

    override suspend fun consolidate(ctx: BondConsolidationContext) {
        bondConsolidationService.consolidate(ctx.buys, ctx.sells, ctx.fullRedemption)
    }

    override suspend fun getConsolidatableIds(): List<Int> {
        TODO("Not yet implemented")
    }
}
