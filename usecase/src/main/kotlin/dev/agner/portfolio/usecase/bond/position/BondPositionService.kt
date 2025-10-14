@file:Suppress("all")
package dev.agner.portfolio.usecase.bond.position

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.PrincipalRedeem
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.TaxIncidence
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.Yield
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.YieldRedeem
import dev.agner.portfolio.usecase.bond.position.model.BondPosition
import dev.agner.portfolio.usecase.bond.statement.BondStatementService
import dev.agner.portfolio.usecase.checkingaccount.position.CheckingAccountPosition
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.tax.TaxService
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BondPositionService(
    private val statementService: BondStatementService,
    private val bondOrderService: BondOrderService,
    private val taxService: TaxService,
) {

    suspend fun calculateByBondId(bondId: Int): List<BondPosition> {
        val statements = statementService.fetchAllByBondId(bondId)
        val buys = bondOrderService.fetchByBondId(bondId).filterIsInstance<Buy>()

        return calculatePositions(statements, buys)
    }

    suspend fun calculateByCheckingAccountId(checkingAccountId: Int): List<CheckingAccountPosition> =
        bondOrderService.fetchByCheckingAccountId(checkingAccountId).filterIsInstance<Contribution.Deposit>()
            .mapAsync {
                val statements = statementService.fetchAllByBondId(it.bond.id)
                calculatePositions(statements, listOf(it))
            }
            .awaitAll()
            .flatten()
            .groupBy { it.date }
            .map { (date, positions) ->
                CheckingAccountPosition(
                    date = date,
                    principal = positions.sumOf { it.principal },
                    yield = positions.sumOf { it.yield },
                    taxes = positions.sumOf { it.taxes },
                    result = positions.sumOf { it.result },
                )
            }
            .sortedBy { it.date }

    private suspend fun calculatePositions(statements: List<BondOrderStatement>, contributions: List<Contribution>): List<BondPosition> {
        val statementsGroupedByDate = statements.groupBy { it.date }
        val contributionsById = contributions.associateBy { it.id }

        return statementsGroupedByDate.keys.sorted()
            .fold(PositionData()) { acc, date ->
                val yieldsOnDateByOrderId = statementsGroupedByDate[date]!!.filterIsInstance<Yield>().associateBy { it.buyOrderId }
                val principalRedeemsByOrderId = statementsGroupedByDate[date]!!.filterIsInstance<PrincipalRedeem>().associateBy { it.buyOrderId }
                val yieldRedeemsGroupedByOrderId = statementsGroupedByDate[date]!!.filter { it is YieldRedeem || it is TaxIncidence }.groupBy { it.buyOrderId }

                val newPrincipal = acc.principal + (contributionsById.values.firstOrNull { it.date == date }?.amount ?: BigDecimal("0.00")) - principalRedeemsByOrderId.values.sumOf { it.amount }
                val newYield = acc.yield + (yieldsOnDateByOrderId.values.sumOf { it.amount }) - yieldRedeemsGroupedByOrderId.values.flatten().sumOf { it.amount }

                val newYieldPerOderId = contributionsById.values.associate { order ->
                     val orderYield = (acc.yieldPerOrder[order.id] ?: BigDecimal("0.00")) + (yieldsOnDateByOrderId[order.id]?.amount ?: BigDecimal("0.00")) - (yieldRedeemsGroupedByOrderId[order.id]?.sumOf { it.amount } ?: BigDecimal("0.00"))

                    order.id to orderYield
                }

                val totalTax = newYieldPerOderId.map { (id, value) ->
                    val taxRate = BigDecimal.ONE - taxService.getTaxIncidencesBy(date, contributionsById[id]!!.date).map { BigDecimal.ONE - it.rate/ BigDecimal("100") }.reduce { acc, d -> acc * d }
                    taxRate * value
                }.reduce { acc, d -> acc + d }

                val position = BondPosition(
                    bond = contributionsById.values.first().bond,
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
