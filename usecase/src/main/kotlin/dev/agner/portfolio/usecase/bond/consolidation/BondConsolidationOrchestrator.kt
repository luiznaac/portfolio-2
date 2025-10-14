package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.DownToZeroContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.RedemptionContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.RedemptionContext.SellContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.RedemptionContext.WithdrawalContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.YieldPercentageContext
import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationResult
import dev.agner.portfolio.usecase.bond.consolidation.model.BondMaturityConsolidationContext
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.bond.model.BondOrder
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Deposit
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullWithdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Sell
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Withdrawal
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.commons.isWeekend
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.commons.yesterday
import dev.agner.portfolio.usecase.index.IndexValueService
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateRange
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

@Service
class BondConsolidationOrchestrator(
    private val repository: IBondOrderStatementRepository,
    private val bondOrderService: BondOrderService,
    private val indexValueService: IndexValueService,
    private val consolidator: BondConsolidator,
    private val clock: Clock,
) {

    // TODO: extract this to use case
    suspend fun consolidateBy(bondId: Int) {
        val orders = bondOrderService.fetchByBondId(bondId)
        val alreadyConsolidatedSells = repository.fetchAlreadyConsolidatedSellIdsByOrderId(bondId)
        val sells = orders
            .filterIsInstance<Sell>()
            .filterNot { alreadyConsolidatedSells.contains(it.id) }

        val alreadyRedeemedBuys = repository.fetchAlreadyRedeemedBuyIdsByOrderId(bondId)
        val buys = orders
            .filterIsInstance<Buy>()
            .filterNot { alreadyRedeemedBuys.contains(it.id) }
            .sortedBy { it.date }

        val fullRedemption = orders.filterIsInstance<FullRedemption>().firstOrNull()

        consolidate(buys, sells, fullRedemption)
    }

    suspend fun consolidate(
        contribution: List<Contribution>,
        redemption: List<Redemption>,
        downToZero: DownToZero?,
    ) {
        val redemptionContexts = redemption.associate { it.date to it.toContext() }
        val downToZeroContext = downToZero?.let { DownToZeroContext(it.id, it.date) }

        contribution
            .sortedBy { it.date }
            .fold(IntermediateData(redemptionContexts)) { acc, contributionOrder ->
                val startingDate = contributionOrder.resolveCalculationStartingDate()
                val finalDate = minOf(LocalDate.yesterday(clock), contributionOrder.bond.maturityDate)
                val yieldPercentages = contributionOrder.bond.buildYieldPercentages(startingDate)
                val startingValues = repository.sumUpConsolidatedValues(contributionOrder.id, startingDate)

                val ctx = BondConsolidationContext(
                    bondOrderId = contributionOrder.id,
                    contributionDate = contributionOrder.date,
                    dateRange = (startingDate..finalDate).removeWeekends(),
                    principal = contributionOrder.amount - startingValues.first,
                    yieldAmount = startingValues.second,
                    yieldPercentages = yieldPercentages,
                    redemptionOrders = acc.remainingRedemptions,
                    downToZeroContext = downToZeroContext,
                )

                val calc = consolidator.calculateBondo(ctx)
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
    }

    private suspend fun BondOrder.resolveCalculationStartingDate() =
        repository.fetchLastByBondOrderId(id)?.date?.nextDay() ?: date

    private suspend fun Bond.buildYieldPercentages(startingAt: LocalDate) = when (this) {
        is FloatingRateBond -> indexValueService.fetchAllBy(indexId, startingAt)
            .associate { it.date to YieldPercentageContext(value, it) }
        is FixedRateBond -> TODO()
    }

    private suspend fun Contribution.handleMaturity(finalDate: LocalDate, result: BondConsolidationResult) =
        if (finalDate == bond.maturityDate && result.principal + result.yieldAmount > BigDecimal("0.00")) {
            val maturityOrderId = createMaturityOrder(finalDate)

            consolidator.consolidateMaturity(
                BondMaturityConsolidationContext(
                    bondOrderId = id,
                    maturityOrderId = maturityOrderId,
                    date = finalDate,
                    contributionDate = date,
                    principal = result.principal,
                    yieldAmount = result.yieldAmount,
                )
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

private fun LocalDateRange.removeWeekends() =
    mapNotNull { it.takeIf { !it.isWeekend() } }

private fun Redemption.toContext() = when (this) {
    is Sell -> SellContext(id, amount)
    is Withdrawal -> WithdrawalContext(id, amount)
}
