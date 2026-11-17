package dev.agner.portfolio.usecase.corporateaction.repository

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSync
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSyncCreation

// The brapi sync audit port, implemented against Exposed in persistence. One row per explicit run,
// carrying the window, the counts, the failed tickers and the raw payload; the latest row is what
// the page shows after a reload.
interface ICorporateActionSyncRepository {

    suspend fun save(creation: CorporateActionSyncCreation): CorporateActionSync

    suspend fun fetchLatest(): CorporateActionSync?
}
