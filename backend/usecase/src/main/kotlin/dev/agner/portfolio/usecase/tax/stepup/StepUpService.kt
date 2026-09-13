package dev.agner.portfolio.usecase.tax.stepup

import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.tax.stepup.model.StepUpPlan
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Orchestrates the step-up plan: asks [StepUpCandidateProvider] for the eligible positions after
 * netting out the order plan's own pending sells, then hands them and the month's remaining
 * sale-exemption ceiling to [StepUpPlanner]. A ticker the plan already sells only enters with the
 * quantity the plan does not cover, so the two lists never propose the same shares twice.
 */
@Service
class StepUpService(
    private val candidateProvider: StepUpCandidateProvider,
    private val orderPlanService: OrderPlanService,
    private val planner: StepUpPlanner,
    private val clock: Clock,
) {

    suspend fun plan(): StepUpPlan {
        val today = LocalDate.today(clock)
        val orderPlan = orderPlanService.computePlan()
        val plannedSells = orderPlan.orders
            .filter { it.kind == OrderKind.SELL || it.kind == OrderKind.FULL_EXIT }
            .associate { it.listedAssetId to it.quantity }

        val candidates = candidateProvider.build(today, plannedSells)

        return planner.plan(candidates, orderPlan.saleCeiling.remaining, today)
    }
}
