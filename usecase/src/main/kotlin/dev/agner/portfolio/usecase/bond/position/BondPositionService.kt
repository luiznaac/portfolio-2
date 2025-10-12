@file:Suppress("all")
package dev.agner.portfolio.usecase.bond.position

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.model.BondOrderType.BUY
import dev.agner.portfolio.usecase.bond.position.model.BondPosition
import dev.agner.portfolio.usecase.bond.statement.BondStatementService
import dev.agner.portfolio.usecase.tax.TaxService
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BondPositionService(
    private val statementService: BondStatementService,
    private val bondOrderService: BondOrderService,
    private val taxService: TaxService,
) {

    suspend fun calculatePositions(bondId: Int): List<BondPosition> {
        val statementsGroupedByDate = statementService.fetchAllByBondId(bondId).groupBy { it.date }
        val ordersById = bondOrderService.fetchByBondId(bondId).filter { it.type == BUY }.associateBy { it.id }
        val bond = ordersById.values.first().bond!!

        return statementsGroupedByDate.keys.sorted()
            .fold(PositionData()) { acc, date ->
                val yieldsOnDateByOrderId = statementsGroupedByDate[date]!!.filter { it.type == "YIELD" }.associateBy { it.buyOrderId }
                val principalRedeemsByOrderId = statementsGroupedByDate[date]!!.filter { it.type == "PRINCIPAL_REDEEM" }.associateBy { it.buyOrderId }
                val yieldRedeemsGroupedByOrderId = statementsGroupedByDate[date]!!.filter { it.type == "YIELD_REDEEM" || it.type.contains("_TAX") }.groupBy { it.buyOrderId }

                val newPrincipal = acc.principal + (ordersById.values.firstOrNull { it.date == date }?.amount ?: BigDecimal("0.00")) - principalRedeemsByOrderId.values.sumOf { it.amount }
                val newYield = acc.yield + (yieldsOnDateByOrderId.values.sumOf { it.amount }) - yieldRedeemsGroupedByOrderId.values.flatten().sumOf { it.amount }

                val newYieldPerOderId = ordersById.values.associate { order ->
                     val orderYield = (acc.yieldPerOrder[order.id] ?: BigDecimal("0.00")) + (yieldsOnDateByOrderId[order.id]?.amount ?: BigDecimal("0.00")) - (yieldRedeemsGroupedByOrderId[order.id]?.sumOf { it.amount } ?: BigDecimal("0.00"))

                    order.id to orderYield
                }

                val totalTax = newYieldPerOderId.map { (id, value) ->
                    val taxRate = BigDecimal.ONE - taxService.getTaxIncidencesBy(date, ordersById[id]!!.date).map { BigDecimal.ONE - it.rate/ BigDecimal("100") }.reduce { acc, d -> acc * d }
                    taxRate * value
                }.reduce { acc, d -> acc + d }

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

private data class PositionData(val principal: BigDecimal = BigDecimal("0.00"), val yield: BigDecimal = BigDecimal("0.00"), val yieldPerOrder: Map<Int, BigDecimal> = emptyMap(), val positions: List<BondPosition> = emptyList())
