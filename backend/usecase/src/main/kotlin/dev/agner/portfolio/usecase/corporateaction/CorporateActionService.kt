package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionResolution
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateCashMovement
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.model.TaxNature
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import org.springframework.stereotype.Service

// The corporate-action engine. An imported event is either applied to the holder's position (the
// quantity events and a capital return), paid as cash (amortization and a cash bonus), remapped in
// the catalog (a ticker change), or parked for review when the engine cannot know what to do — a
// rights issue, a merger ratio, or an unclassified movement row. Everything runs inside one
// transaction so a failure cannot leave the log, the position, the slices and the cash ledger
// disagreeing.
@Service
class CorporateActionService(
    private val repository: ICorporateActionRepository,
    private val attributions: IAttributionRepository,
    private val catalog: CatalogService,
    private val positionService: PositionService,
    private val transaction: ITransactionTemplate,
) {

    suspend fun fetchAll() = repository.fetchAll()

    // Applies a batch in the deterministic replay order — ex date, then the event's own ordinal.
    // The log's natural key is the dedup: a re-export of a period already applied is a no-op.
    suspend fun importEvents(creations: List<CorporateActionCreation>): List<CorporateAction> =
        transaction.execute {
            val existingRows = repository.fetchAll()
            val existing = existingRows.map { it.naturalKey() }.toSet()
            val importedHashes = existingRows.mapNotNull { it.fileHash }.toSet()
            val batchHashes = creations.mapNotNull { it.fileHash }.toSet()
            // The file hash is the file-level guard: re-uploading the same Movimentação file is a
            // no-op even if a row's ordinal changed in a re-save. The natural key still dedups a
            // different file that overlaps the period.
            if (batchHashes.any { it in importedHashes }) return@execute emptyList()
            val fresh = creations
                .map { it.normalized() }
                .distinctBy { it.naturalKey() }
                .filter { it.naturalKey() !in existing }
                .sortedWith(compareBy({ it.exDate }, { it.ordinal }, { it.kind.name }))
            fresh.map { apply(it) }
        }

    // The owner's decision on a parked event. The supplied data fills the columns the B3 export
    // lacked and the engine then applies the event exactly as an import would; a kind the engine can
    // never compute (a rights issue, a merger) is the owner's acknowledgement that it was handled
    // outside the app, so it joins the applied history with no ledger effect.
    suspend fun resolve(id: Int, resolution: CorporateActionResolution): CorporateAction =
        transaction.execute {
            val parked = repository.fetchById(id) ?: throw CorporateActionNotFoundException(id)
            if (parked.status != CorporateActionStatus.NEEDS_REVIEW) {
                throw InvalidCorporateActionResolutionException(id, "the event is already ${parked.status}")
            }
            val creation = resolution.mergedWith(parked)
            // A computable kind still needs its missing column; an incomplete decision is rejected
            // rather than silently acknowledging an event the position replay cannot apply.
            if (creation.needsReview() && !creation.kind.requiresReview) {
                throw InvalidCorporateActionResolutionException(
                    id,
                    "the event still lacks the data the engine needs",
                )
            }
            // A remap with no source entry would be recorded as applied and change nothing; the
            // missing catalog row has to be repaired before the decision can land.
            if (creation.kind == CorporateActionKind.TICKER_CHANGE && catalog.fetchBy(creation.ticker) == null) {
                throw InvalidCorporateActionResolutionException(
                    id,
                    "ticker ${creation.ticker} is absent from the catalog",
                )
            }
            val resolved = repository.update(id, creation, CorporateActionStatus.APPLIED)
                ?: throw CorporateActionNotFoundException(id)
            if (creation.kind.requiresReview) return@execute resolved
            settle(resolved)
        }

    private suspend fun apply(creation: CorporateActionCreation): CorporateAction {
        val status = when {
            creation.needsReview() -> CorporateActionStatus.NEEDS_REVIEW
            // A remap with no source entry would be silently applied and change nothing; queue it
            // so the missing catalog row is visible instead.
            creation.kind == CorporateActionKind.TICKER_CHANGE && catalog.fetchBy(creation.ticker) == null ->
                CorporateActionStatus.NEEDS_REVIEW
            else -> CorporateActionStatus.APPLIED
        }
        val action = repository.save(creation, status)
        if (status == CorporateActionStatus.NEEDS_REVIEW) return action
        return settle(action)
    }

    // The ledger effects of an applied event, shared by the import and the human resolve: the remap
    // or the quantity scaling, the cash entitlement, and the reprojection the position read needs.
    private suspend fun settle(action: CorporateAction): CorporateAction {
        when {
            action.kind == CorporateActionKind.TICKER_CHANGE -> remapTicker(action)
            action.kind.isQuantityEvent -> scaleAttributions(action)
        }
        // Cash is read off the position before the replay: a grupamento's fraction belongs to the
        // pre-event quantity, and a capital return's per-share amount to the unchanged quantity.
        recordCash(action)
        if (action.kind.isQuantityEvent || action.kind == CorporateActionKind.CAPITAL_RETURN) {
            positionService.reproject(action.ticker)
        }
        return action
    }

    // The fraction a grupamento could not convert is paid in cash at the prior average price; a
    // cash-only event pays its declared per-share value. Both use the position before the replay so
    // the amount is the holder's actual entitlement, not a post-event guess.
    private suspend fun recordCash(action: CorporateAction) {
        val position = positionService.positionBefore(action)
        val amount = CorporateActionCalculator.cashAmount(position, action) ?: return
        repository.saveCashMovement(
            CorporateCashMovement(
                ticker = action.ticker,
                date = action.exDate,
                amount = amount,
                taxNature = cashNature(action),
                corporateActionId = action.id,
            ),
        )
    }

    private suspend fun remapTicker(action: CorporateAction) {
        action.targetTicker?.let { catalog.remapTicker(action.ticker, it) }
    }

    private suspend fun scaleAttributions(action: CorporateAction) {
        attributions.fetchBy(action.ticker).forEach { slice ->
            attributions.save(
                slice.copy(quantity = CorporateActionCalculator.scaleQuantity(slice.quantity, action)),
            )
        }
    }

    private fun cashNature(action: CorporateAction): TaxNature =
        action.kind.cashTaxNature ?: TaxNature.OTHER
}
