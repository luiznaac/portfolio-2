package dev.agner.portfolio.usecase.catalog

import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.ClassificationQueueItem
import dev.agner.portfolio.usecase.catalog.model.UNCATALOGED_BDI_TYPE
import dev.agner.portfolio.usecase.catalog.model.UnclassifiedReason
import dev.agner.portfolio.usecase.catalog.model.effectiveAssetClass
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import org.springframework.stereotype.Service

// The held-unclassified residue the bulk classification modal reads: every held ticker with no
// effective class, one row each, with the reason the catalog cannot classify it. The reason is
// read from the stored row, not from the last refresh: a row the COTAHIST mapping does not know is
// an unhandled BDI code, while a row still on the import's placeholder — or one the file never
// entered — is absent from the latest file. There is no default class; the queue is never guessed.
@Service
class ClassificationQueueService(
    private val positions: IPositionRepository,
    private val catalog: ICatalogRepository,
) {

    suspend fun fetchQueue(): List<ClassificationQueueItem> {
        val entries = catalog.fetchAll().associateBy { it.ticker }
        return positions.fetchAll()
            .filter { it.quantity.signum() > 0 }
            .filter { entries[it.ticker]?.effectiveAssetClass == null }
            .groupBy { it.ticker }
            .map { (ticker, rows) ->
                val entry = entries[ticker]
                ClassificationQueueItem(
                    ticker = ticker,
                    shortName = entry?.shortName ?: ticker,
                    quantity = rows.sumOf { it.quantity },
                    reason = reasonOf(entry),
                )
            }
            .sortedBy { it.ticker }
    }

    private fun reasonOf(entry: CatalogEntry?): UnclassifiedReason =
        if (entry?.bdiType == null || entry.bdiType == UNCATALOGED_BDI_TYPE) {
            UnclassifiedReason.ABSENT_FROM_LATEST_FILE
        } else {
            UnclassifiedReason.UNHANDLED_BDI_CODE
        }
}
