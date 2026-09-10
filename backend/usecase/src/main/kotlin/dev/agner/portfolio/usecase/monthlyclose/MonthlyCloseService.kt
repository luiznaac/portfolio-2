package dev.agner.portfolio.usecase.monthlyclose

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.monthlyclose.model.DriftAlert
import dev.agner.portfolio.usecase.monthlyclose.model.MonthlyClose
import dev.agner.portfolio.usecase.monthlyclose.repository.IMonthlyCloseRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.TransferProposalStatus.PENDING
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock

/**
 * The month's close as a state machine (OPEN -> CLOSED) plus a drift check against the current
 * allocation plan. On demand only — there is no scheduler here.
 */
@Service
class MonthlyCloseService(
    private val repository: IMonthlyCloseRepository,
    private val allocationService: AllocationService,
    private val orderPlanService: OrderPlanService,
    private val transaction: ITransactionTemplate,
    private val clock: Clock,
) {

    suspend fun current(): MonthlyClose = repository.open(currentMonth())

    suspend fun history(): List<MonthlyClose> = repository.fetchAll()

    /**
     * A transfer proposal changes what the order list looks like — approved means a smaller net
     * trade, rejected means the full buy and sell — so closing with one still undecided would lock
     * in a plan that might still change.
     */
    suspend fun close(): MonthlyClose {
        val pending = orderPlanService.transfersForMonth().count { it.status == PENDING }
        require(pending == 0) { "Cannot close the month with $pending pending transfer proposal(s)" }

        val month = currentMonth()
        return transaction.execute {
            repository.open(month)
            repository.close(month)
        }
    }

    suspend fun driftAlert(thresholdPP: BigDecimal = DEFAULT_THRESHOLD_PP): List<DriftAlert> {
        val plan = allocationService.currentPlan()
        if (plan.capital.signum() == 0) return emptyList()

        return plan.classes.mapNotNull { node ->
            val currentWeight = node.current.divide(plan.capital, 6, RoundingMode.HALF_EVEN)
            val driftPP = (currentWeight - node.idealWeight).abs()

            if (driftPP > thresholdPP) {
                DriftAlert(
                    assetClass = node.assetClass,
                    idealWeight = node.idealWeight,
                    currentWeight = currentWeight,
                    driftPP = driftPP,
                )
            } else {
                null
            }
        }
    }

    private fun currentMonth(): LocalDate {
        val today = LocalDate.today(clock)
        return LocalDate(today.year, today.month, 1)
    }

    private companion object {
        val DEFAULT_THRESHOLD_PP: BigDecimal = BigDecimal("0.05")
    }
}
