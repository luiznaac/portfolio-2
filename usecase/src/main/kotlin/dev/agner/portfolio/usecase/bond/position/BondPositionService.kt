@file:Suppress("all")
package dev.agner.portfolio.usecase.bond.position

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.model.BondOrderType.BUY
import dev.agner.portfolio.usecase.bond.position.model.BondPosition
import dev.agner.portfolio.usecase.bond.statement.BondStatementService
import dev.agner.portfolio.usecase.tax.TaxService
import org.springframework.stereotype.Service

@Service
class BondPositionService(
    private val statementService: BondStatementService,
    private val bondOrderService: BondOrderService,
    private val taxService: TaxService,
) {

    suspend fun calculatePositions(bondId: Int): List<BondPosition> {
        val statementsGroupedByDate = statementService.fetchAllByBondId(bondId).groupBy { it.date }
        val ordersById = bondOrderService.fetchByBondId(bondId).filter { it.type == BUY }.associateBy { it.id }
        val bond = ordersById.values.first().bond

        return statementsGroupedByDate.keys.sorted()
            .fold(PositionData(0.0, 0.0, emptyMap(), emptyList())) { acc, date ->
                val yieldsOnDateByOrderId = statementsGroupedByDate[date]!!.filter { it.type == "YIELD" }.associateBy { it.buyOrderId }
                val principalRedeemsByOrderId = statementsGroupedByDate[date]!!.filter { it.type == "PRINCIPAL_REDEEM" }.associateBy { it.buyOrderId }
                val yieldRedeemsGroupedByOrderId = statementsGroupedByDate[date]!!.filter { it.type == "YIELD_REDEEM" || it.type.contains("_TAX") }.groupBy { it.buyOrderId }

                val newPrincipal = acc.principal + (ordersById.values.firstOrNull { it.date == date }?.amount ?: 0.0) - principalRedeemsByOrderId.values.sumOf { it.amount }
                val newYield = acc.yield + (yieldsOnDateByOrderId.values.sumOf { it.amount }) - yieldRedeemsGroupedByOrderId.values.flatten().sumOf { it.amount }

                val newYieldPerOderId = ordersById.values.associate { order ->
                     val orderYield = (acc.yieldPerOrder[order.id] ?: 0.0) + (yieldsOnDateByOrderId[order.id]?.amount ?: 0.0) - (yieldRedeemsGroupedByOrderId[order.id]?.sumOf { it.amount } ?: 0.0)

                    order.id to orderYield
                }

                val totalTax = newYieldPerOderId.map { (id, value) ->
                    val taxRate = 1 - taxService.getTaxIncidencesBy(date, ordersById[id]!!.date).map { 1 - it.rate/100 }.reduce { acc, d -> acc * d }
                    taxRate * value
                }.sum()

                val position = BondPosition(
                    bond = bond,
                    date = date,
                    principal = newPrincipal,
                    yield = newYield,
                    taxes = totalTax,
                    result = newPrincipal + newYield - totalTax,
                )

                acc.copy(
                    principal = newPrincipal,
                    yield = newYield,
                    yieldPerOrder = newYieldPerOderId,
                    positions = acc.positions + position,
                )
            }
            .positions
    }
}

private data class PositionData(val principal: Double, val yield: Double, val yieldPerOrder: Map<Int, Double>, val positions: List<BondPosition>)
