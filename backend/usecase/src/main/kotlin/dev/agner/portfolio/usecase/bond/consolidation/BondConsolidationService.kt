package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.DownToZeroContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext.SellContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.RedemptionContext.WithdrawalContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondMaturityConsolidationContext
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Sell
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatement
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.commons.removeWeekends
import dev.agner.portfolio.usecase.commons.yesterday
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

@Service
class BondConsolidationService(
    private val repository: IBondOrderStatementRepository,
    private val bondOrderService: BondOrderService,
    private val yieldRateService: YieldRateService,
    private val contributionConsolidator: BondContributionConsolidator,
    private val clock: Clock,
) {

    suspend fun consolidate(
        contribution: List<Contribution>,
        redemption: List<Redemption>,
        downToZero: DownToZero?,
    ): List<BondOrderStatement> {
        val redemptionContexts = redemption.associate { it.date to it.toContext() }
        val downToZeroContext = downToZero?.let { DownToZeroContext(it.id, it.date) }

        return contribution
            .sortedBy { it.date }
            .fold(IntermediateData(redemptionContexts)) { acc, contributionOrder ->
                val startingDate = contributionOrder.resolveCalculationStartingDate()
                val finalDate = minOf(LocalDate.yesterday(clock), contributionOrder.bond.maturityDate)
                val yieldRates = contributionOrder.bond.buildYieldRates(startingDate)
                val startingValues = repository.sumUpConsolidatedValues(contributionOrder.id, startingDate)

                val ctx = BondContributionConsolidationContext(
                    bondOrderId = contributionOrder.id,
                    contributionDate = contributionOrder.date,
                    dateRange = (startingDate..finalDate).removeWeekends(),
                    principal = contributionOrder.amount - startingValues.first,
                    yieldAmount = startingValues.second,
                    yieldRates = yieldRates,
                    redemptionOrders = acc.remainingRedemptions,
                    downToZeroContext = downToZeroContext,
                )

                val calc = contributionConsolidator.calculateBondo(ctx)
                val maturityStatements = contributionOrder.handleMaturity(finalDate, calc)

                acc.copy(
                    remainingRedemptions = calc.remainingSells,
                    statements = acc.statements + calc.statements + maturityStatements,
                )
            }
            .also { it.remainingRedemptions.values.handleRemaining() }
            .statements
            .chunked(100)
            .mapAsync { repository.saveAll(it) }
            .awaitAll()
            .flatten()
    }

    private suspend fun BondOrder.resolveCalculationStartingDate() =
        repository.fetchLastByBondOrderId(id)?.date?.nextDay() ?: date

    private suspend fun Bond.buildYieldRates(startingAt: LocalDate) =
        yieldRateService.buildRateFor(this, startingAt)

    private suspend fun Contribution.handleMaturity(finalDate: LocalDate, result: BondContributionConsolidationResult) =
        if (finalDate == bond.maturityDate && result.principal + result.yieldAmount > BigDecimal("0.00")) {
            val maturityOrderId = createMaturityOrder(finalDate)

            contributionConsolidator.consolidateMaturity(
                BondMaturityConsolidationContext(
                    bondOrderId = id,
                    maturityOrderId = maturityOrderId,
                    date = finalDate,
                    contributionDate = date,
                    principal = result.principal,
                    yieldAmount = result.yieldAmount,
                ),
            )
        } else {
            emptyList()
        }

    private suspend fun Contribution.createMaturityOrder(date: LocalDate) =
        bondOrderService.create(
            BondOrderCreation(
                bondId = bond.id,
                type = BondOrderType.MATURITY,
                date = date,
                amount = BigDecimal("0.00"),
                checkingAccountId = if (this is Deposit) checkingAccountId else null,
            ),
            isInternal = true,
        ).id

    private suspend fun Collection<RedemptionContext>.handleRemaining() {
        if (size > 1) {
            throw IllegalStateException("There is more than one remaining sell")
        }

        if (size == 1) {
            val remainingRedemption = first()

            bondOrderService.updateType(
                id = remainingRedemption.id,
                type = when (remainingRedemption) {
                    is SellContext -> FullRedemption::class
                    is WithdrawalContext -> FullWithdrawal::class
                },
            )
        }
    }

    private data class IntermediateData(
        val remainingRedemptions: Map<LocalDate, RedemptionContext>,
        val statements: List<BondOrderStatementCreation> = emptyList(),
    )
}

private fun Redemption.toContext() = when (this) {
    is Sell -> SellContext(id, amount)
    is Withdrawal -> WithdrawalContext(id, amount)
}
