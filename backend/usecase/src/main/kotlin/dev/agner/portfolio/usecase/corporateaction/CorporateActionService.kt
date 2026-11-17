package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
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
import dev.agner.portfolio.usecase.listedasset.RightsAllotmentService
import dev.agner.portfolio.usecase.listedasset.repository.IAttributionRepository
import dev.agner.portfolio.usecase.tickeralias.TickerAliasResolver
import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

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
    private val tickerAliases: ITickerAliasRepository,
    private val aliases: TickerAliasResolver,
    private val rightsAllotments: RightsAllotmentService,
    private val transaction: ITransactionTemplate,
) {

    // The log read presents each event under the paper's canonical ticker, so a rename does not
    // hide the old code's history. The stored row keeps the code the export carried.
    suspend fun fetchAll(): List<CorporateAction> {
        val rows = repository.fetchAll()
        val canonicalByTicker = aliases.canonicalAll(
            rows.flatMap { listOfNotNull(it.ticker, it.targetTicker) }.distinct(),
        )
        return rows.map { action ->
            action.copy(
                ticker = canonicalByTicker.getValue(action.ticker),
                targetTicker = action.targetTicker?.let { canonicalByTicker.getValue(it) },
            )
        }
    }

    // Applies a batch in the deterministic replay order — ex date, then a rename before any other
    // event of its own date, then the event's own ordinal. The log's natural key is the dedup: a
    // re-export of a period already applied is a no-op.
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
                .sortedWith(REPLAY_ORDER)
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
            val settled = creation.withComputedGrant()
            val resolved = repository.update(id, settled, CorporateActionStatus.APPLIED)
                ?: throw CorporateActionNotFoundException(id)
            if (settled.kind.requiresReview) return@execute resolved
            settle(resolved)
        }

    // The brapi one-off sync's write path. Unlike an import, an event whose natural key is already
    // in the log is not simply skipped: a B3 row wins on the values it carries, while brapi fills
    // the columns the export lacked and the engine then settles the event it could not apply
    // before. A row that is already applied (from either source) is a no-op, so a re-run never
    // duplicates an event or applies it twice.
    suspend fun applyBrapi(creations: List<CorporateActionCreation>): BrapiApplyResult =
        transaction.execute {
            val existing = repository.fetchAll().associateBy { it.naturalKey() }
            var applied = 0
            var parked = 0
            val fresh = mutableListOf<CorporateActionCreation>()
            for (creation in creations.map { it.normalized() }.distinctBy { it.naturalKey() }) {
                val current = existing[creation.naturalKey()] ?: orphanOf(creation, existing.values)
                when {
                    current == null -> fresh += creation
                    current.isApplied -> Unit
                    else -> {
                        val merged = current.enrichedWith(creation)
                        val status = statusFor(merged)
                        val updated = repository.update(current.id, merged, status)
                            ?: throw CorporateActionNotFoundException(current.id)
                        if (status == CorporateActionStatus.APPLIED) {
                            settle(updated)
                            applied++
                        } else {
                            parked++
                        }
                    }
                }
            }
            for (creation in fresh.sortedWith(REPLAY_ORDER)) {
                val action = apply(creation)
                if (action.isApplied) applied++ else parked++
            }
            BrapiApplyResult(applied = applied, parked = parked)
        }

    // A parked row with no ex-date is the same event a later payload now dates: match it instead of
    // inserting a second row, but only when exactly one candidate exists — two undated rows of one
    // kind are ambiguous and the sync must not guess which one the date belongs to.
    private fun orphanOf(
        creation: CorporateActionCreation,
        existing: Collection<CorporateAction>,
    ): CorporateAction? {
        if (creation.exDate == null) return null
        return existing
            .filter { !it.isApplied && it.exDate == null && it.ticker == creation.ticker && it.kind == creation.kind }
            .singleOrNull()
    }

    private suspend fun apply(creation: CorporateActionCreation): CorporateAction {
        val status = statusFor(creation)
        val action = repository.save(
            if (status == CorporateActionStatus.APPLIED) creation.withComputedGrant() else creation,
            status,
        )
        if (status == CorporateActionStatus.NEEDS_REVIEW) return action
        return settle(action)
    }

    // A rights allotment's grant is the holder's underlying units before the ex-date times the
    // issuance factor; the engine computes it once, when the event settles, and stores it so the
    // position replay needs no cross-ticker read. Every other kind is stored as imported.
    private suspend fun CorporateActionCreation.withComputedGrant(): CorporateActionCreation {
        if (kind != CorporateActionKind.RIGHTS_ALLOTMENT) return this
        val underlying = targetTicker ?: return this
        val factor = ratio ?: return this
        val date = exDate ?: return this
        val held = positionService.positionBefore(underlying, date).quantity
        val granted = held.multiply(factor).setScale(0, RoundingMode.FLOOR)
        return copy(grantedQuantity = granted.takeIf { it.signum() > 0 })
    }

    private suspend fun statusFor(creation: CorporateActionCreation): CorporateActionStatus = when {
        creation.needsReview() -> CorporateActionStatus.NEEDS_REVIEW
        // A remap with no source entry would be silently applied and change nothing; queue it so
        // the missing catalog row is visible instead.
        creation.kind == CorporateActionKind.TICKER_CHANGE && catalog.fetchBy(creation.ticker) == null ->
            CorporateActionStatus.NEEDS_REVIEW
        else -> CorporateActionStatus.APPLIED
    }

    // The ledger effects of an applied event, shared by the import and the human resolve: the remap
    // or the quantity scaling, the cash entitlement, and the reprojection the position read needs.
    private suspend fun settle(action: CorporateAction): CorporateAction {
        when {
            action.kind == CorporateActionKind.TICKER_CHANGE -> {
                remapTicker(action)
                // A rename carrying a conversion ratio moves the book exactly like a split, in the
                // same event that writes the alias.
                if (action.hasConversionRatio) scaleAttributions(action)
                // The projection moves to the new code in the same event, so the read never shows
                // two rows for one paper.
                positionService.reproject(action.ticker)
            }
            action.kind.isQuantityEvent -> scaleAttributions(action)
            action.kind == CorporateActionKind.RIGHTS_ALLOTMENT -> {
                // The rights paper is now real: mark it, retire the derived guess a sale without a
                // receipt may have created, and replay the rights position from the stored grant.
                // An applied event always has its ex-date (a null one parks in needsReview).
                catalog.markRights(action.ticker)
                action.exDate?.let { rightsAllotments.replaceDerived(action.ticker, it) }
                positionService.reproject(action.ticker)
            }
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
        // Only an applied event pays cash, and an applied event always carries its ex-date.
        val exDate = action.exDate ?: return
        val position = positionService.positionBefore(action)
        val amount = CorporateActionCalculator.cashAmount(position, action) ?: return
        repository.saveCashMovement(
            CorporateCashMovement(
                ticker = action.ticker,
                date = exDate,
                amount = amount,
                taxNature = cashNature(action),
                corporateActionId = action.id,
            ),
        )
    }

    // The catalog remap and the alias edge are one atomic event: the resolver can never see the new
    // code in the catalog without the chain that points the old code at it.
    private suspend fun remapTicker(action: CorporateAction) {
        val target = action.targetTicker ?: return
        val exDate = action.exDate ?: return
        catalog.remapTicker(action.ticker, target)
        tickerAliases.save(
            TickerAlias(
                oldTicker = action.ticker.normalizedTicker(),
                canonicalTicker = target.normalizedTicker(),
                effectiveDate = exDate,
                source = action.source,
                ratio = action.ratio,
                corporateActionId = action.id,
            ),
        )
    }

    // Every stored slice of the paper scales, not only the ones keyed to the event's own code: after
    // a rename the slices may still sit under the old code, and a same-date quantity event can be
    // keyed to the new one. The alias set is what names the paper's whole book.
    private suspend fun scaleAttributions(action: CorporateAction) {
        aliases.aliasesOf(action.ticker)
            .flatMap { attributions.fetchBy(it) }
            .forEach { slice ->
                attributions.save(
                    slice.copy(quantity = CorporateActionCalculator.scaleQuantity(slice.quantity, action)),
                )
            }
    }

    private fun cashNature(action: CorporateAction): TaxNature =
        action.kind.cashTaxNature ?: TaxNature.OTHER
}

// A rename carrying a conversion ratio other than 1 scales the book exactly like a split at its
// effective date. A plain rename (no ratio, or a ratio of 1) only moves the identity, so its slices
// are not rewritten. A private extension rather than a model property, so the edge DTO keeps
// serialising only the stored columns and `is_applied`.
private val CorporateAction.hasConversionRatio: Boolean
    get() = kind == CorporateActionKind.TICKER_CHANGE &&
        ratio != null &&
        ratio.signum() > 0 &&
        ratio.compareTo(BigDecimal.ONE) != 0

// The deterministic replay order the import and the brapi sync share: ex date, then a rename before
// any other event of its own date, then the event's own ordinal.
private val REPLAY_ORDER = compareBy<CorporateActionCreation>(
    { it.exDate },
    { it.kind.replayRank },
    { it.ordinal },
    { it.kind.name },
)

// What one brapi apply did: the events the engine settled and the ones still waiting for a human.
data class BrapiApplyResult(
    val applied: Int,
    val parked: Int,
)

// The parked row enriched by a brapi event: the stored value wins where it has one (B3 is
// authoritative), and the incoming event only fills a missing or unusable column. The source, the
// identity and the provenance columns stay the row's own.
private fun CorporateAction.enrichedWith(creation: CorporateActionCreation) = CorporateActionCreation(
    ticker = ticker,
    kind = kind,
    exDate = exDate ?: creation.exDate,
    approvalDate = approvalDate ?: creation.approvalDate,
    ratio = ratio.positiveOrNull() ?: creation.ratio,
    declaredValuePerShare = declaredValuePerShare.positiveOrNull() ?: creation.declaredValuePerShare,
    targetTicker = targetTicker ?: creation.targetTicker,
    source = source,
    processNumber = processNumber,
    rawType = rawType ?: creation.rawType,
    ordinal = ordinal,
    fileHash = fileHash,
)

private fun BigDecimal?.positiveOrNull(): BigDecimal? = this?.takeIf { it.signum() > 0 }
