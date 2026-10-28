package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionAdd
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionApplied
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionGroup
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionPreview
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionResult
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkip
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkipReason
import dev.agner.portfolio.usecase.listedasset.model.toLedger
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.order.PlacementCalculator
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.TargetChainCalculator
import dev.agner.portfolio.usecase.portfolio.targetedTickers
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.StrategyKind
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service

// The page-level bulk action that moves every outside Position into its class's Manual Strategy.
// The read is the pure planner's plan; the write reuses AttributionService.setSlice per Ticker, so
// the class match and the custody ceiling stay enforced in one place. Each Ticker is written on its
// own, so one bad Ticker is reported beside the successes instead of aborting the run.
@Service
class LegacyAttributionService(
    private val positions: IPositionRepository,
    private val catalog: ICatalogRepository,
    private val strategies: IStrategyRepository,
    private val quotes: QuoteService,
    private val months: MonthService,
    private val attributions: AttributionService,
) {

    suspend fun preview(): LegacyAttributionPreview {
        val held = positions.fetchAll()
        val classByTicker = catalog.fetchAll().associate { it.ticker to it.assetClass }
        val strategyList = strategies.fetchAll()
        val manualByClass = strategyList
            .filter { it.kind == StrategyKind.MANUAL }
            .associateBy { it.assetClass }
        val slicesByTicker = attributions.fetchSlicesByTicker()
        val targeted = months.fetchCurrent()
            ?.let { TargetChainCalculator.compute(it.month.capital, it.targets.normalized()).targetedTickers() }
            .orEmpty()
        val placement = PlacementCalculator.compute(
            positions = held.associate { it.ticker to it.quantity },
            targetedTickers = targeted,
            ledger = slicesByTicker.toLedger(),
            manualStrategies = manualByClass.values.map { it.name }.toSet(),
            unclassifiedTickers = classByTicker.filterValues { it == null }.keys,
        )
        val prices = held
            .mapAsync { position -> position.ticker to quotes.latestClose(position.ticker) }
            .awaitAll()
            .mapNotNull { (ticker, price) -> price?.let { ticker to it } }
            .toMap()

        return LegacyAttributionPlanner.plan(
            positions = held,
            classByTicker = classByTicker,
            placement = placement,
            slicesByTicker = slicesByTicker,
            manualByClass = manualByClass,
            prices = prices,
        )
    }

    suspend fun apply(): LegacyAttributionResult {
        val preview = preview()
        val outcomes = preview.groups.flatMap { group -> group.adds.map { add -> write(group, add) } }
        return LegacyAttributionResult(
            attributed = outcomes.filterIsInstance<WriteOutcome.Applied>().map { it.applied },
            skipped = preview.skips + outcomes.filterIsInstance<WriteOutcome.Skipped>().map { it.skip },
        )
    }

    private suspend fun write(group: LegacyAttributionGroup, add: LegacyAttributionAdd): WriteOutcome =
        try {
            val summary = attributions.setSlice(
                add.ticker,
                AttributionSliceCreation(strategyId = group.strategyId, quantity = add.quantity),
            )
            if (summary == null) {
                WriteOutcome.Skipped(skip(add, group, "position or strategy not found"))
            } else {
                WriteOutcome.Applied(
                    LegacyAttributionApplied(
                        ticker = add.ticker,
                        quantity = add.quantity,
                        value = add.value,
                        strategyId = group.strategyId,
                        strategyName = group.strategyName,
                    ),
                )
            }
        } catch (error: InvalidAttributionException) {
            WriteOutcome.Skipped(skip(add, group, error.detail))
        }

    private fun skip(add: LegacyAttributionAdd, group: LegacyAttributionGroup, message: String) =
        LegacyAttributionSkip(
            ticker = add.ticker,
            reason = LegacyAttributionSkipReason.WRITE_FAILED,
            assetClass = group.assetClass,
            message = message,
        )

    private sealed interface WriteOutcome {
        data class Applied(val applied: LegacyAttributionApplied) : WriteOutcome
        data class Skipped(val skip: LegacyAttributionSkip) : WriteOutcome
    }
}
