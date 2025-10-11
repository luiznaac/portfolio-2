package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.FullRedemptionContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.SellOrderContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.YieldPercentageContext
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.commons.yesterday
import dev.agner.portfolio.usecase.index.IndexValueService
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateRange
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class BondConsolidationOrchestrator(
    private val repository: IBondOrderStatementRepository,
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
    private val consolidator: BondConsolidator,
    private val clock: Clock,
) {

    suspend fun consolidateBy(bondId: Int) {
        val orders = bondOrderService.fetchByBondId(bondId)
        val alreadyConsolidatedSells = repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId)
        val sellOrders = orders
            .filter { it.type == BondOrderType.SELL }
            .filterNot { alreadyConsolidatedSells.contains(it.id) }

        val alreadyRedeemedBuys = repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId)
        val buyOrders = orders
            .filter { it.type == BondOrderType.BUY }
            .filterNot { alreadyRedeemedBuys.contains(it.id) }
            .sortedBy { it.date }

        val fullRedemptionOrder = orders.firstOrNull { it.type == BondOrderType.FULL_REDEMPTION }

        consolidate(buyOrders, sellOrders, fullRedemptionOrder)
    }

    // TODO(): Later it can be extract to be reused with checking accounts (that will aggregate multiple
    //         bonds and thus it'll have to consider all orders from different bonds at the same time)
    private suspend fun consolidate(
        buyOrders: List<BondOrder>,
        sellOrders: List<BondOrder>,
        fullRedemptionOrder: BondOrder?,
    ) {
        val sellContexts = sellOrders.associate { it.date to SellOrderContext(it.id, it.amount) }
        val fullRedemptionContext = fullRedemptionOrder?.let { FullRedemptionContext(it.id, it.date) }

        buyOrders
            .fold(IntermediateData(sellContexts)) { acc, order ->
                val startingDate = order.resolveCalculationStartingDate()
                val yieldPercentages = order.bond.buildYieldPercentages(startingDate)
                val startingValues = repository.sumUpConsolidatedValues(order.id, startingDate)
                val ctx = BondConsolidationContext(
                    bondOrderId = order.id,
                    contributionDate = order.date,
                    dateRange = (startingDate..LocalDate.yesterday(clock)).removeWeekends(),
                    principal = order.amount - startingValues.first,
                    yieldAmount = startingValues.second,
                    yieldPercentages = yieldPercentages,
                    sellOrders = acc.remainingSells,
                    fullRedemption = fullRedemptionContext,
                )

                val calc = consolidator.calculateBondo(ctx)

                acc.copy(
                    remainingSells = calc.remainingSells,
                    statements = acc.statements + calc.statements,
                )
            }
            .also { it.remainingSells.values.handleRemainingSells() }
            .statements
            .chunked(100)
            .mapAsync { repository.saveAll(it) }
            .awaitAll()
    }

    private suspend fun BondOrder.resolveCalculationStartingDate() =
        repository.fetchLastByBondOrderId(id)?.date?.nextDay() ?: date

    private suspend fun Bond.buildYieldPercentages(startingAt: LocalDate) = when (this) {
        is FloatingRateBond -> indexValueService.fetchAllBy(indexId, startingAt)
            .associate { it.date to YieldPercentageContext(value, it) }
        is FixedRateBond -> TODO()
    }

    private suspend fun Collection<SellOrderContext>.handleRemainingSells() {
        if (size > 1) {
            throw IllegalStateException("There is more than one remaining sell")
        }

        if (size == 1) {
            val remainingSell = first()

            bondOrderService.updateAmount(
                remainingSell.id,
                bondOrderService.fetchById(remainingSell.id).amount - remainingSell.amount,
            )
        }
    }

    private data class IntermediateData(
        val remainingSells: Map<LocalDate, SellOrderContext>,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}

private fun LocalDateRange.removeWeekends() =
    mapNotNull { it.takeIf { !listOf(SATURDAY, SUNDAY).contains(it.dayOfWeek) } }
