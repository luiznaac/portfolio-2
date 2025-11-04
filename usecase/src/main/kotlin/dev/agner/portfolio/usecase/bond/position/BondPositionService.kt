package dev.agner.portfolio.usecase.bond.position

import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.PrincipalRedeem
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.TaxIncidence
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.Yield
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement.YieldRedeem
import dev.agner.portfolio.usecase.bond.position.model.BondOrderPosition
import dev.agner.portfolio.usecase.bond.position.model.BondPosition
import dev.agner.portfolio.usecase.bond.position.repository.IBondOrderPositionRepository
import dev.agner.portfolio.usecase.checkingaccount.position.CheckingAccountPosition
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.tax.TaxService
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class BondPositionService(
    private val repository: IBondOrderPositionRepository,
    private val bondService: BondService,
    private val taxService: TaxService,
) {

    suspend fun getLastByBondId(bondId: Int) = getByBondId(bondId).last()

    suspend fun getByBondId(bondId: Int) = repository.fetchByBondId(bondId)
        .groupBy { it.date }
        .map { (date, positions) ->
            BondPosition(
                date = date,
                principal = positions.sumOf { it.principal },
                yield = positions.sumOf { it.yield },
                taxes = positions.sumOf { it.taxes },
            )
        }
        .sortedBy { it.date }

    suspend fun getLastByCheckingAccountId(checkingAccountId: Int) = getByCheckingAccountId(checkingAccountId).last()

    suspend fun getByCheckingAccountId(checkingAccountId: Int) =
        bondService.fetchByCheckingAccountId(checkingAccountId)
            .mapAsync { getByBondId(it.id) }
            .awaitAll()
            .flatten()
            .groupBy { it.date }
            .map { (date, positions) ->
                CheckingAccountPosition(
                    date = date,
                    principal = positions.sumOf { it.principal },
                    yield = positions.sumOf { it.yield },
                    taxes = positions.sumOf { it.taxes },
                )
            }
            .sortedBy { it.date }

    suspend fun consolidatePositions(statements: List<BondOrderStatement>, contributions: List<Contribution>) {
        val statementsByDateAndOrderId = statements.groupBy { it.buyOrderId }.mapValues { it.value.groupBy { it.date } }
        val contributionsById = contributions.associateBy { it.id }
        val positionDataByOrderId = repository.fetchLastByBondOrderId(contributions.map { it.id })
            .associate { it.bondOrderId to it.toPositionData() }

        statementsByDateAndOrderId.map {
            val statementsByDate = it.value
            val contribution = contributionsById[it.key]!!
            val positionData = positionDataByOrderId[contribution.id] ?: PositionData(principal = contribution.amount)

            it.value.keys.sorted()
                .fold(positionData) { acc, date ->
                    val yield = statementsByDate[date]!!
                        .filterIsInstance<Yield>().singleOrNull()?.amount ?: BigDecimal.ZERO
                    val principalRedeem = statementsByDate[date]!!
                        .filterIsInstance<PrincipalRedeem>().singleOrNull()?.amount ?: BigDecimal.ZERO
                    val yieldRedeems = statementsByDate[date]!!.filter { it is YieldRedeem || it is TaxIncidence }

                    val newPrincipal = acc.principal - principalRedeem
                    val newYield = acc.yield + yield - yieldRedeems.sumOf { it.amount }

                    val taxRate = BigDecimal.ONE -
                        taxService.getTaxIncidencesBy(date, contribution.date)
                            .map { BigDecimal.ONE - it.rate / BigDecimal("100") }
                            .reduce { acc, d -> acc * d }
                    val totalTax = (taxRate * newYield).defaultScale()

                    val position = BondOrderPosition(
                        bondOrderId = contribution.id,
                        date = date,
                        principal = newPrincipal,
                        yield = newYield,
                        taxes = totalTax,
                    )

                    acc.copy(
                        principal = newPrincipal,
                        yield = newYield,
                        positions = acc.positions + position,
                    )
                }
                .positions
        }
            .flatten()
            .chunked(100)
            .onEach { repository.saveAll(it) }
    }
}

private fun BondOrderPosition.toPositionData() = PositionData(
    principal = principal,
    yield = yield,
)

private data class PositionData(
    val principal: BigDecimal,
    val yield: BigDecimal = BigDecimal("0.00"),
    val positions: List<BondOrderPosition> = emptyList(),
)
