package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.effectiveAssetClass
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlicesByTicker
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.AttributionTransferCreation
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.toLedger
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The attribution ledger: parts of a position's custody mapped to strategies, with the remainder
// left explicitly unattributed. The position is the ceiling, so a write is rejected when the slices
// would sum above it (Σ attributions ≤ Position.quantity). A slice also has to match its Strategy's
// Asset Class — the class-match rule that keeps the per-class sums and the shared-ticker split safe
// — and an unclassified ticker cannot be attributed at all. Reads never write; the two mutations run
// inside a transaction so the checks and the write see the same ledger.
//
// A renamed paper's slices can still sit under an old code, so every code in the alias chain is one
// book: the reads span the alias set, the ceiling counts all of them, and a write lands on the
// canonical code — an old-code slice is superseded, never left beside it as a second row.
@Service
class AttributionService(
    private val attributions: IAttributionRepository,
    private val positions: IPositionRepository,
    private val strategies: IStrategyRepository,
    private val catalog: ICatalogRepository,
    private val aliases: TickerAliasResolver,
    private val transaction: ITransactionTemplate,
) {

    // The ledger keyed by ticker, each slice carrying its strategy's name — the read the order
    // engine and the harvest pass both need. The id-to-name projection belongs here, beside the
    // other reads that join it.
    suspend fun fetchSlicesByTicker(): AttributionSlicesByTicker {
        val names = strategies.fetchAll().associate { it.id to it.name }
        val rows = attributions.fetchAll()
        val canonicalByTicker = aliases.canonicalAll(rows.map { it.ticker })
        return rows
            .groupBy { canonicalByTicker.getValue(it.ticker) }
            // Two stored rows can collapse onto one canonical slice (a rename before the slices
            // were remapped); the ledger presents one slice per (ticker, strategy), summing them.
            .mapValues { (_, group) -> collapse(group, names) }
    }

    // One slice per strategy with the quantities summed: a strategy's slice can be split across the
    // codes of a rename chain, and every read presents it as one entry.
    private fun collapse(rows: List<Attribution>, names: Map<Int, String>): List<AttributionSlice> =
        rows
            .groupBy { it.strategyId }
            .mapNotNull { (strategyId, group) ->
                names[strategyId]?.let { name ->
                    AttributionSlice(
                        strategyId = strategyId,
                        strategyName = name,
                        quantity = group.sumOf { it.quantity },
                    )
                }
            }
            .sortedBy { it.strategyName }

    // The whole ledger keyed by (strategy name, ticker), the way the order engine's funding pass
    // consumes it. It is the same slice read flattened, so the two can never disagree.
    suspend fun fetchLedger(): Map<StrategyTicker, BigDecimal> = fetchSlicesByTicker().toLedger()

    suspend fun fetchSummary(ticker: String): AttributionSummary? {
        val normalized = aliases.canonical(ticker)
        val position = positions.fetchBy(normalized) ?: return null
        return summarize(normalized, position, storedSlices(normalized))
    }

    // Null means the ticker has no position or the strategy does not exist — the controller maps
    // that to 404. A slice that breaks the invariant is a caller error and throws.
    suspend fun setSlice(ticker: String, creation: AttributionSliceCreation): AttributionSummary? {
        if (creation.quantity <= BigDecimal.ZERO) {
            throw InvalidAttributionException("quantity must be positive, received ${creation.quantity}")
        }
        return upsertSlice(normalized = aliases.canonical(ticker), creation = creation)
    }

    // The one Attribution Transfer operation: the source slice gives up units, the destination slice
    // gains them, and a source that reaches zero is deleted. Custody never changes, and the checks
    // (positive quantity, distinct strategies, class match, source holding enough) all live here so
    // the manual endpoint and the order lifecycle cannot drift into two implementations.
    suspend fun transfer(ticker: String, creation: AttributionTransferCreation): AttributionSummary? {
        if (creation.quantity <= BigDecimal.ZERO) {
            throw InvalidTransferException("quantity must be positive, received ${creation.quantity}")
        }
        if (creation.fromStrategyId == creation.toStrategyId) {
            throw InvalidTransferException("source and destination must be different strategies")
        }
        return moveSlice(normalized = aliases.canonical(ticker), creation = creation)
    }

    suspend fun removeSlice(ticker: String, strategyId: Int): AttributionSummary? {
        val normalized = aliases.canonical(ticker)
        return transaction.execute {
            val position = positions.fetchBy(normalized)
            if (position == null) {
                null
            } else {
                val aliasSet = aliases.aliasesOf(normalized)
                val remaining = aliasSet
                    .flatMap { attributions.fetchBy(it) }
                    .filterNot { it.strategyId == strategyId }
                for (code in aliasSet) attributions.delete(code, strategyId)
                summarize(normalized, position, remaining)
            }
        }
    }

    // Every stored slice of the paper, whichever code in the rename chain it was written with — the
    // one read the summary, the ceiling and the two mutations share.
    private suspend fun storedSlices(normalized: String): List<Attribution> =
        aliases.aliasesOf(normalized).flatMap { attributions.fetchBy(it) }

    // A write lands on the canonical code, so the rows the paper still stores under a retired code
    // are superseded rather than left beside it as a second slice for the same strategy.
    private suspend fun deleteRetiredRows(rows: List<Attribution>, canonical: String, strategyIds: Set<Int>) {
        for (row in rows.filter { it.strategyId in strategyIds && it.ticker != canonical }) {
            attributions.delete(row.ticker, row.strategyId)
        }
    }

    private suspend fun upsertSlice(
        normalized: String,
        creation: AttributionSliceCreation,
    ): AttributionSummary? = transaction.execute {
        val position = positions.fetchBy(normalized)
        val strategy = strategies.fetchById(creation.strategyId)
        if (position == null || strategy == null) {
            null
        } else {
            requireClassMatch(normalized, strategy)
            val existing = storedSlices(normalized)

            // Replacing a strategy's slice counts only the other strategies against the ceiling;
            // the slice being replaced is the one this write overwrites.
            val retainedRows = existing.filterNot { it.strategyId == strategy.id }
            val retained = retainedRows.sumOf { it.quantity }
            if (retained + creation.quantity > position.quantity) {
                throw InvalidAttributionException(
                    "cannot attribute ${creation.quantity} to ${strategy.name}: " +
                        "${position.quantity - retained} of ${position.quantity} available",
                )
            }

            deleteRetiredRows(existing, normalized, setOf(strategy.id))
            attributions.save(Attribution(normalized, strategy.id, creation.quantity))
            summarize(
                normalized,
                position,
                retainedRows + Attribution(normalized, strategy.id, creation.quantity),
            )
        }
    }

    private suspend fun moveSlice(
        normalized: String,
        creation: AttributionTransferCreation,
    ): AttributionSummary? = transaction.execute {
        val position = positions.fetchBy(normalized)
        val from = strategies.fetchById(creation.fromStrategyId)
        val to = strategies.fetchById(creation.toStrategyId)
        if (position == null || from == null || to == null) {
            null
        } else {
            requireClassMatch(normalized, from) { InvalidTransferException(it) }
            requireClassMatch(normalized, to) { InvalidTransferException(it) }

            val rows = storedSlices(normalized)
            val sourceQuantity = rows.filter { it.strategyId == from.id }.sumOf { it.quantity }
            if (sourceQuantity < creation.quantity) {
                throw InvalidTransferException(
                    "$normalized strategy ${from.name} holds $sourceQuantity, but the transfer moves " +
                        creation.quantity,
                )
            }

            val remaining = sourceQuantity - creation.quantity
            val sourceRow = remaining.takeIf { it.signum() > 0 }?.let { Attribution(normalized, from.id, it) }
            val destinationQuantity = rows.filter { it.strategyId == to.id }.sumOf { it.quantity } +
                creation.quantity
            val destinationRow = Attribution(normalized, to.id, destinationQuantity)

            // Both strategies end with one slice, keyed on the canonical code: any old-code row
            // they still hold is superseded by the rows written below.
            deleteRetiredRows(rows, normalized, setOf(from.id, to.id))

            if (sourceRow == null) {
                attributions.delete(normalized, from.id)
            } else {
                attributions.save(sourceRow)
            }
            attributions.save(destinationRow)

            val moved = rows.filterNot { it.strategyId == from.id || it.strategyId == to.id } +
                listOfNotNull(sourceRow, destinationRow)
            summarize(normalized, position, moved)
        }
    }

    // A ticker enters a Strategy only once the catalog gives it a class, and only the Strategy of
    // that class may hold it. Classification is a precondition, not an inference. The caller names
    // the error because a slice write and a transfer are different caller errors.
    private suspend fun requireClassMatch(
        ticker: String,
        strategy: Strategy,
        onMismatch: (String) -> DomainException = { InvalidAttributionException(it) },
    ) {
        val assetClass = catalog.fetchBy(ticker)?.effectiveAssetClass
        val reason = if (assetClass == null) {
            "$ticker has no Asset Class; classify it before attributing"
        } else if (assetClass != strategy.assetClass) {
            "cannot attribute $ticker ($assetClass) to ${strategy.name} (${strategy.assetClass})"
        } else {
            null
        }
        if (reason != null) throw onMismatch(reason)
    }

    private suspend fun summarize(
        ticker: String,
        position: Position,
        rows: List<Attribution>,
    ): AttributionSummary {
        val names = strategies.fetchAll().associate { it.id to it.name }
        val slices = collapse(rows, names)
        val attributed = rows.sumOf { it.quantity }
        return AttributionSummary(
            ticker = ticker,
            custodyQuantity = position.quantity,
            attributedQuantity = attributed,
            unattributedQuantity = position.quantity - attributed,
            slices = slices,
        )
    }
}
