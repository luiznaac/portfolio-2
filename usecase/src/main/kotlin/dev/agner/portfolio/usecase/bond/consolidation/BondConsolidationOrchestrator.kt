package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.SellOrderContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.YieldPercentageContext
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.extension.nextDay
import dev.agner.portfolio.usecase.index.IndexValueService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondConsolidationOrchestrator(
    private val repository: IBondOrderStatementRepository, // TODO(): Create service and use that instead
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
    private val consolidator: BondConsolidator,
) {

    // TODO(): Handle FixedRateBonds
    // TODO(): In the future, we'll need to handle multiple users, so this should take a userId as well
    suspend fun consolidateBy(bondId: Int) {
        // TODO(): Refactor to hit the DB less often
        val alreadyRedeemedBuys = repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId)
        val orders = bondOrderService.fetchByBondId(bondId)
            .filterNot { alreadyRedeemedBuys.contains(it.id) }
            .sortedBy { it.date }
        val alreadyConsolidatedSells = repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId)
        val sellOrders = orders
            .filter { it.type == BondOrderType.SELL }
            .filterNot { alreadyConsolidatedSells.contains(it.id) }
            .associate {
                it.date to SellOrderContext(it.id, it.amount)
            }

        orders
            .filter { it.type == BondOrderType.BUY }
            .fold(IntermediateData(sellOrders)) { acc, order ->
                val startingDate = order.resolveCalculationStartingDate()
                val yieldPercentages = order.bond.buildYieldPercentages(startingDate)
                val startingValues = repository.sumUpConsolidatedValues(order.id, startingDate)
                val ctx = BondConsolidationContext(
                    bondOrderId = order.id,
                    contributionDate = order.date,
                    principal = order.amount - startingValues.first,
                    yieldAmount = startingValues.second,
                    yieldPercentages = yieldPercentages,
                    sellOrders = acc.remainingSells,
                )

                val calc = consolidator.calculateBondo(ctx)

                acc.copy(
                    remainingSells = calc.remainingSells,
                    statements = acc.statements + calc.statements,
                )
            }
            .also {
                // TODO(): validate there's no sell remaining
            }
            .statements
            .chunked(100)
            .onEach { repository.saveAll(it) }
    }

    private suspend fun BondOrder.resolveCalculationStartingDate() =
        repository.fetchLastByBondOrderId(id)?.date?.nextDay() ?: date

    private suspend fun Bond.buildYieldPercentages(startingAt: LocalDate) = when (this) {
        is FloatingRateBond -> indexValueService.fetchAllBy(indexId, startingAt)
            .associate { it.date to YieldPercentageContext(value, it) }
        is FixedRateBond -> TODO()
    }

    private data class IntermediateData(
        val remainingSells: Map<LocalDate, SellOrderContext>,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}
