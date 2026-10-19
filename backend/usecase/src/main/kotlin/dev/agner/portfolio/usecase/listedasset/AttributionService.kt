package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.listedasset.model.Attribution
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The attribution ledger: parts of a position's custody mapped to strategies, with the remainder
// left explicitly unattributed. The position is the ceiling, so a write is rejected when the slices
// would sum above it (Σ attributions ≤ Position.quantity). Reads never write; the two mutations run
// inside a transaction so the ceiling check and the write see the same ledger.
@Service
class AttributionService(
    private val attributions: IAttributionRepository,
    private val positions: IPositionRepository,
    private val strategies: IStrategyRepository,
    private val transaction: ITransactionTemplate,
) {

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
