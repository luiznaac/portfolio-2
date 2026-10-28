package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.PositionReconciliation
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PositionReconciliationService(
    private val positions: IPositionRepository,
) {

    // Custody vs ledger, per ticker, over the union of both sides: a ticker the ledger holds but
    // custody does not (and vice versa) is drift too, so neither side is assumed complete. Read
    // only — a drift is surfaced as attention and never edits the ledger.
    suspend fun reconcile(custody: List<CustodyPosition>): List<PositionReconciliation> {
        val ledger = positions.fetchAll().associateBy { it.ticker }
        val custodyByTicker = custody
            .groupBy { it.ticker.normalizedTicker() }
            .mapValues { (_, rows) -> rows.map { it.quantity }.fold(BigDecimal.ZERO, BigDecimal::add) }

        return (ledger.keys + custodyByTicker.keys).sorted().map { ticker ->
            PositionReconciliation(
                ticker = ticker,
                custodyQuantity = custodyByTicker[ticker] ?: BigDecimal.ZERO,
                ledgerQuantity = ledger[ticker]?.quantity ?: BigDecimal.ZERO,
            )
        }
    }
}
