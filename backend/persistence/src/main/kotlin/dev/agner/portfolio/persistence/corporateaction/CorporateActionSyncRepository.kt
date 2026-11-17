package dev.agner.portfolio.persistence.corporateaction

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSync
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSyncCreation
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionSyncRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class CorporateActionSyncRepository : ICorporateActionSyncRepository {

    override suspend fun save(creation: CorporateActionSyncCreation): CorporateActionSync = transaction {
        CorporateActionSyncEntity.new {
            startedAt = creation.startedAt
            asOf = creation.asOf
            windowFrom = creation.windowFrom
            windowTo = creation.windowTo
            tickerCount = creation.tickerCount
            fetchedCount = creation.fetched
            appliedCount = creation.applied
            parkedCount = creation.parked
            incomeCount = creation.income
            failedTickers = creation.failedTickers.joinToString(",")
            rawPayload = creation.rawPayload
        }.toModel()
    }

    override suspend fun fetchLatest(): CorporateActionSync? = transaction {
        CorporateActionSyncEntity.all()
            .orderBy(CorporateActionSyncTable.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toModel()
    }
}
