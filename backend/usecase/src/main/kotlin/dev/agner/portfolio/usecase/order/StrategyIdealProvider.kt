package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

/**
 * The per-strategy inputs an order plan is built from: the strategy names, each strategy's share of
 * its asset class's ideal capital (current weight × class ideal), and its latest edition's target
 * weight per ticker.
 *
 * Extracted from [OrderPlanAssembler] so the assembly loop only consumes ready-fetched maps.
 */
@Component
class StrategyIdealProvider(
    private val strategyService: StrategyService,
    private val strategyWeightRepository: IStrategyWeightRepository,
    private val strategyEditionService: StrategyEditionService,
    private val allocationService: AllocationService,
    private val clock: Clock,
) {

    suspend fun fetch(): StrategyPlanContext {
        val today = LocalDate.today(clock)
        val strategies = strategyService.fetchAll()
        val strategyNames = strategies.associate { it.id to it.name }

        val weightByStrategy = strategyWeightRepository.fetchCurrent(today).associate { it.strategyId to it.weight }
        val classIdealByClass = allocationService.currentPlan().classes.associate { it.assetClass to it.ideal }
        val idealCapitalByStrategy = strategies.associate { strategy ->
            val weight = weightByStrategy[strategy.id] ?: BigDecimal.ZERO
            val classIdeal = classIdealByClass[strategy.assetClass] ?: BigDecimal.ZERO
            strategy.id to (weight * classIdeal)
        }

        // Only the latest edition drives the plan — a ticker dropped there is a full exit, not a
        // stale top-up — and a strategy with no edition yet targets nothing.
        val targetsByStrategy = strategies.associate { strategy ->
            val latest = strategyEditionService.fetchEditions(strategy.id).lastOrNull()?.edition
            strategy.id to latest?.targets.orEmpty().associate { it.ticker to it.weight }
        }

        return StrategyPlanContext(
            strategyNames = strategyNames,
            idealCapitalByStrategy = idealCapitalByStrategy,
            targetsByStrategy = targetsByStrategy,
        )
    }
}

/** One [StrategyIdealProvider.fetch] result: every strategy-side input the assembler consumes. */
data class StrategyPlanContext(
    val strategyNames: Map<Int, String>,
    val idealCapitalByStrategy: Map<Int, BigDecimal>,
    val targetsByStrategy: Map<Int, Map<String, BigDecimal>>,
)
