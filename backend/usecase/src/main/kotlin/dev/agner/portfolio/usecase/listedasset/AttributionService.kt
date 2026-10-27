package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The attribution ledger: parts of a position's custody mapped to strategies, with the remainder
// left explicitly unattributed. The position is the ceiling, so a write is rejected when the slices
// would sum above it (Σ attributions ≤ Position.quantity). A slice also has to match its Strategy's
// Asset Class — the class-match rule that keeps the per-class sums and the shared-ticker split safe
// — and an unclassified ticker cannot be attributed at all. Reads never write; the two mutations run
// inside a transaction so the checks and the write see the same ledger.
@Service
class AttributionService(
    private val attributions: IAttributionRepository,
    private val positions: IPositionRepository,
    private val strategies: IStrategyRepository,
    private val catalog: ICatalogRepository,
    private val transaction: ITransactionTemplate,
) {

    // The ledger keyed by ticker, each slice carrying its strategy's name — the read the order
    // engine and the harvest pass both need. The id-to-name projection belongs here, beside the
    // other reads that join it.
    suspend fun fetchSlicesByTicker(): Map<String, List<AttributionSlice>> {
        val names = strategies.fetchAll().associate { it.id to it.name }
        return attributions.fetchAll()
            .mapNotNull { row ->
                names[row.strategyId]?.let { name ->
                    row.ticker to AttributionSlice(row.strategyId, name, row.quantity)
                }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    }

    // The whole ledger keyed by (strategy name, ticker), the way the order engine's funding pass
    // consumes it. It is the same slice read flattened, so the two can never disagree.
    suspend fun fetchLedger(): Map<StrategyTicker, BigDecimal> =
        fetchSlicesByTicker()
            .flatMap { (ticker, slices) ->
                slices.map { StrategyTicker(it.strategyName, ticker) to it.quantity }
            }
            .toMap()

    suspend fun fetchSummary(ticker: String): AttributionSummary? {
        val normalized = ticker.normalizedTicker()
        val position = positions.fetchBy(normalized) ?: return null
        return summarize(normalized, position, attributions.fetchBy(normalized))
    }

    // Null means the ticker has no position or the strategy does not exist — the controller maps
    // that to 404. A slice that breaks the invariant is a caller error and throws.
    suspend fun setSlice(ticker: String, creation: AttributionSliceCreation): AttributionSummary? {
        if (creation.quantity <= BigDecimal.ZERO) {
            throw InvalidAttributionException("quantity must be positive, received ${creation.quantity}")
        }
        return upsertSlice(normalized = ticker.normalizedTicker(), creation = creation)
    }

    suspend fun removeSlice(ticker: String, strategyId: Int): AttributionSummary? {
        val normalized = ticker.normalizedTicker()
        return transaction.execute {
            val position = positions.fetchBy(normalized)
            if (position == null) {
                null
            } else {
                attributions.delete(normalized, strategyId)
                summarize(normalized, position, attributions.fetchBy(normalized))
            }
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
            val existing = attributions.fetchBy(normalized)

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

            attributions.save(Attribution(normalized, strategy.id, creation.quantity))
            summarize(
                normalized,
                position,
                retainedRows + Attribution(normalized, strategy.id, creation.quantity),
            )
        }
    }

    // A ticker enters a Strategy only once the catalog gives it a class, and only the Strategy of
    // that class may hold it. Classification is a precondition, not an inference.
    private suspend fun requireClassMatch(ticker: String, strategy: Strategy) {
        val assetClass = catalog.fetchBy(ticker)?.assetClass
            ?: throw InvalidAttributionException("$ticker has no Asset Class; classify it before attributing")
        if (assetClass != strategy.assetClass) {
            throw InvalidAttributionException(
                "cannot attribute $ticker ($assetClass) to ${strategy.name} (${strategy.assetClass})",
            )
        }
    }

    private suspend fun summarize(
        ticker: String,
        position: Position,
        rows: List<Attribution>,
    ): AttributionSummary {
        val names = strategies.fetchAll().associate { it.id to it.name }
        val slices = rows
            .mapNotNull { row ->
                names[row.strategyId]?.let { name ->
                    AttributionSlice(strategyId = row.strategyId, strategyName = name, quantity = row.quantity)
                }
            }
            .sortedBy { it.strategyName }
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
