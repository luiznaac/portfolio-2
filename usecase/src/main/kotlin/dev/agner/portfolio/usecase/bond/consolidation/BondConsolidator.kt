package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.BondConsolidationContext.SellOrderContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.PrincipalRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.Yield
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationRecord.YieldRedeem
import dev.agner.portfolio.usecase.bond.consolidation.model.BondCalculationResult
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.model.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.FloatingRateBond
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.extension.nextDay
import dev.agner.portfolio.usecase.extension.mapWithData
import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexValue
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class BondConsolidator(
    private val repository: IBondOrderStatementRepository, // TODO(): Create service and use that instead
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
    private val calculator: BondCalculator,
) {

    // TODO(): Handle FixedRateBonds
    // TODO(): In the future, we'll need to handle multiple users, so this should take a userId as well
    suspend fun consolidateBy(bondId: Int) {
        // TODO(): [1] Return a consolidation position so it's not necessary anymore to do calculations on BondOrder.initData
        val orders = bondOrderService.fetchByBondId(bondId).sortedBy { it.date }
        // TODO(): Remover sells que ja foram previamente consolidadas
        val sellOrders = orders.filter { it.type == BondOrderType.SELL }.map { SellOrderContext(it.id, it.date, it.amount) }

        orders
            .filter { it.type == BondOrderType.BUY }
            .mapWithData(sellOrders) { sells, order ->
                val startingDate = order.resolveCalculationStartingDate()
                val yieldPercentages = order.bond.buildYieldPercentages(startingDate)
                    .associate { p -> p.date to p.percentage }

                val calc = calculateBondo(yieldPercentages, order, startingDate, sells)

                calc.ctx.sellOrders to calc.statements
            }
            .flatten()
            .chunked(100)
            .onEach { repository.saveAll(it) }
    }

    private suspend fun calculateBondo(
        yieldPercentages: Map<LocalDate, Double>,
        order: BondOrder,
        startingDate: LocalDate,
        sells: List<SellOrderContext>
    ): IntermediateData = yieldPercentages
        .keys.sorted() // TODO(): Calculate dates programmatically - doing this now because we must get holidays right
        // TODO(): Passar direto toda a info necess'aria, contexto ja construido
        .fold(order.initData(startingDate, sells)) { acc, date ->
            // TODO(): Remove calculated SELL so it does not appear in the next BUY order iteration
            val sellOrder = acc.ctx.sellOrders.find { it.date == date }
            val result = calculator.calculate(
                BondCalculationContext(
                    acc.ctx.principal,
                    acc.ctx.yieldAmount,
                    yieldPercentages[date]!!,
                    sellOrder?.amount ?: 0.0
                )
            )

            acc.updateWith(result, date)
                .run {
                    if (sellOrder != null) {
                        val newSells = ctx.sellOrders - sellOrder
                        copy(ctx = ctx.copy(sellOrders = newSells))
                    } else this
                }
        }

    private fun IntermediateData.updateWith(result: BondCalculationResult, date: LocalDate) =
        copy(ctx = ctx.copy(principal = result.principal, yieldAmount = result.yield), statements = statements + result.statements.map { it.buildCreation(ctx.bondOrderId, date, 0) })

    private suspend fun BondOrder.resolveCalculationStartingDate() =
        repository.fetchLastByBondOrderId(id)?.date?.nextDay() ?: date

    // TODO(): Extract to separate service
    private suspend fun Bond.buildYieldPercentages(startingAt: LocalDate) = when (this) {
        is FloatingRateBond -> indexValueService.fetchAllBy(indexId, startingAt).map { YieldPercentage(value, it) }
        is FixedRateBond -> TODO()
        else -> throw IllegalStateException("Unknown bond type: ${this::class.simpleName}")
    }

    private suspend fun BondOrder.initData(startingAt: LocalDate, sells: List<SellOrderContext>): IntermediateData {
        // TODO: Remove (refer to [1])
        val consolidatedValues = repository.sumUpConsolidatedValues(id, startingAt)
        return IntermediateData(
            BondConsolidationContext(
                id,
                amount - consolidatedValues.first,
                consolidatedValues.second,
                sells,
            ),
        emptyList(),
        )
    }

    private fun BondCalculationRecord.buildCreation(bondOrderId: Int, date: LocalDate, sellOrderId: Int?) =
        when (this) {
            is Yield -> BondOrderStatementCreation.Yield(bondOrderId, date, amount)
            is YieldRedeem -> BondOrderStatementCreation.YieldRedeem(bondOrderId, date, amount, sellOrderId!!)
            is PrincipalRedeem -> BondOrderStatementCreation.PrincipalRedeem(bondOrderId, date, amount, sellOrderId!!)
        }
}

private data class IntermediateData(
    val ctx: BondConsolidationContext,
    val statements: List<BondOrderStatementCreation>,
)

data class BondConsolidationContext(
    val bondOrderId: Int,
    val principal: Double,
    val yieldAmount: Double,
    val sellOrders: List<SellOrderContext> = emptyList(),
) {
    data class SellOrderContext(
        val id: Int,
        val date: LocalDate,
        val amount: Double,
    )
}

private data class YieldPercentage(
    val date: LocalDate,
    val percentage: Double,
) {
    constructor(multiplier: Double, indexValue: IndexValue) : this(
        date = indexValue.date,
        percentage = (multiplier / 100) * indexValue.value,
    )
}
