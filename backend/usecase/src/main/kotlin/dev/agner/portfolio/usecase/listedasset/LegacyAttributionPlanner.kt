package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.valueAt
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlicesByTicker
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionAdd
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionGroup
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionPreview
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkip
import dev.agner.portfolio.usecase.listedasset.model.LegacyAttributionSkipReason
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.order.model.PlacedTicker
import dev.agner.portfolio.usecase.order.model.Placement
import dev.agner.portfolio.usecase.strategy.model.Strategy
import java.math.BigDecimal

// The bulk legacy action's candidate rule. It reads the shared placement verdict — a Ticker is
// outside only when no Target names it and no Manual Strategy holds it — and adds the bulk action's
// own conditions: a report-driven Attribution keeps the Ticker out entirely, an unclassified Ticker
// waits for classification, and a class without a bucket is reported instead of silently skipped.
// A Ticker the bucket already holds absorbs the custody delta both ways: its slice's new total is the
// whole custody, so the action is idempotent and a buy or a sale is folded in by re-running it.
object LegacyAttributionPlanner {

    fun plan(
        positions: List<Position>,
        classByTicker: Map<String, AssetClass?>,
        placement: Map<String, PlacedTicker>,
        slicesByTicker: AttributionSlicesByTicker,
        manualByClass: Map<AssetClass, Strategy>,
        prices: Map<String, BigDecimal>,
    ): LegacyAttributionPreview {
        val outcomes = positions
            .filter { it.quantity.signum() > 0 }
            .sortedBy { it.ticker }
            .map { position ->
                outcomeOf(
                    position = position,
                    assetClass = classByTicker[position.ticker],
                    placement = placement[position.ticker],
                    slices = slicesByTicker[position.ticker].orEmpty(),
                    manualByClass = manualByClass,
                    prices = prices,
                )
            }

        val addsByClass = outcomes.filterIsInstance<Outcome.Add>().groupBy { it.assetClass }
        val groups = AssetClass.entries.mapNotNull { assetClass ->
            val adds = addsByClass[assetClass] ?: return@mapNotNull null
            val bucket = manualByClass.getValue(assetClass)
            LegacyAttributionGroup(
                assetClass = assetClass,
                strategyId = bucket.id,
                strategyName = bucket.name,
                adds = adds.map { it.add },
            )
        }
        val skips = outcomes.filterIsInstance<Outcome.Skip>().map { it.skip }.sortedBy { it.ticker }
        return LegacyAttributionPreview(groups = groups, skips = skips)
    }

    private fun outcomeOf(
        position: Position,
        assetClass: AssetClass?,
        placement: PlacedTicker?,
        slices: List<AttributionSlice>,
        manualByClass: Map<AssetClass, Strategy>,
        prices: Map<String, BigDecimal>,
    ): Outcome {
        if (assetClass == null) {
            return Outcome.Skip(skip(position.ticker, LegacyAttributionSkipReason.UNCLASSIFIED, null))
        }
        val bucket = manualByClass[assetClass]
        if (slices.isNotEmpty()) {
            // A slice to any other Strategy — the inert report-driven one included — keeps the Ticker
            // out of the bulk action; a mixed slice would hide its data-quality problem.
            if (bucket == null || slices.any { it.strategyId != bucket.id }) {
                return Outcome.Skip(
                    skip(position.ticker, LegacyAttributionSkipReason.HAS_ATTRIBUTION, assetClass),
                )
            }
            val bucketQuantity = slices.sumOf { it.quantity }
            return if (bucketQuantity.compareTo(position.quantity) == 0) {
                Outcome.None
            } else {
                Outcome.Add(assetClass, add(position, prices))
            }
        }
        return when (placement?.placement) {
            Placement.OUTSIDE ->
                if (bucket == null) {
                    Outcome.Skip(skip(position.ticker, LegacyAttributionSkipReason.NO_BUCKET, assetClass))
                } else {
                    Outcome.Add(assetClass, add(position, prices))
                }
            Placement.UNCLASSIFIED ->
                Outcome.Skip(skip(position.ticker, LegacyAttributionSkipReason.UNCLASSIFIED, null))
            // Inside by a Target: the report Strategy plans it, so the bucket must not claim it.
            Placement.INSIDE, null -> Outcome.None
        }
    }

    private fun add(position: Position, prices: Map<String, BigDecimal>) = LegacyAttributionAdd(
        ticker = position.ticker,
        quantity = position.quantity,
        value = position.quantity.valueAt(price = prices[position.ticker]),
    )

    private fun skip(ticker: String, reason: LegacyAttributionSkipReason, assetClass: AssetClass?) =
        LegacyAttributionSkip(ticker = ticker, reason = reason, assetClass = assetClass)

    private sealed interface Outcome {
        data class Add(val assetClass: AssetClass, val add: LegacyAttributionAdd) : Outcome
        data class Skip(val skip: LegacyAttributionSkip) : Outcome
        data object None : Outcome
    }
}
