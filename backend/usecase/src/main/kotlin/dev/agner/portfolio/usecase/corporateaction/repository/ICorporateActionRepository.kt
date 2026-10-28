package dev.agner.portfolio.usecase.corporateaction.repository

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateCashMovement

// The corporate-action log port, implemented against Exposed in persistence. The log is both the
// event ledger (replayed by PositionCalculator) and the review queue, so reads expose both applied
// and review-queued rows; `save` decides the status the caller derived.
interface ICorporateActionRepository {

    // Newest first — the log page's order.
    suspend fun fetchAll(): List<CorporateAction>

    // Oldest first with the replay's deterministic tie-break (ex date, ordinal, id).
    suspend fun fetchByTicker(ticker: String): List<CorporateAction>

    // Every applied event across tickers, for a whole-book reprojection.
    suspend fun fetchApplied(): List<CorporateAction>

    suspend fun save(
        creation: CorporateActionCreation,
        status: CorporateActionStatus,
    ): CorporateAction

    // Persists a corporate-action cash movement into the shared `cash_movement` ledger, linked to
    // the event that produced it.
    suspend fun saveCashMovement(movement: CorporateCashMovement)
}
