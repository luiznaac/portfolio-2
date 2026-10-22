package dev.agner.portfolio.persistence.corporateaction

import dev.agner.portfolio.persistence.income.CashMovementEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.model.CorporateCashMovement
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.Provenance
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class CorporateActionRepository(
    private val clock: Clock,
) : ICorporateActionRepository {

    override suspend fun fetchAll(): List<CorporateAction> = transaction {
        CorporateActionEntity.all()
            .orderBy(
                CorporateActionTable.exDate to SortOrder.DESC,
                CorporateActionTable.id to SortOrder.DESC,
            )
            .map { it.toModel() }
    }

    override suspend fun fetchByTicker(ticker: String): List<CorporateAction> = transaction {
        CorporateActionEntity.find { CorporateActionTable.ticker eq ticker }
            .orderBy(
                CorporateActionTable.exDate to SortOrder.ASC,
                CorporateActionTable.ordinal to SortOrder.ASC,
                CorporateActionTable.id to SortOrder.ASC,
            )
            .map { it.toModel() }
    }

    override suspend fun fetchApplied(): List<CorporateAction> = transaction {
        CorporateActionEntity.find { CorporateActionTable.status eq CorporateActionStatus.APPLIED }
            .orderBy(
                CorporateActionTable.exDate to SortOrder.ASC,
                CorporateActionTable.ordinal to SortOrder.ASC,
                CorporateActionTable.id to SortOrder.ASC,
            )
            .map { it.toModel() }
    }

    override suspend fun save(
        creation: CorporateActionCreation,
        status: CorporateActionStatus,
    ): CorporateAction = transaction {
        CorporateActionEntity.new {
            ticker = creation.ticker
            kind = creation.kind
            exDate = creation.exDate
            approvalDate = creation.approvalDate
            ratio = creation.ratio
            declaredValuePerShare = creation.declaredValuePerShare
            targetTicker = creation.targetTicker
            sourceType = creation.source
            processNumber = creation.processNumber
            rawType = creation.rawType
            ordinal = creation.ordinal
            fileHash = creation.fileHash
            this.status = status
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    // Corporate-action cash shares the `cash_movement` table with income but carries a non-INCOME
    // tax nature, so the Income reads never see it. There is no income type on these rows; the
    // event link is what identifies them.
    override suspend fun saveCashMovement(movement: CorporateCashMovement) {
        transaction {
            CashMovementEntity.new {
                ticker = movement.ticker
                date = movement.date
                grossAmount = movement.amount
                retainedAmount = BigDecimal.ZERO
                netAmount = movement.amount
                taxNature = movement.taxNature
                provenance = Provenance.B3
                status = IncomeStatus.RECONCILED
                incomeType = null
                corporateActionId = movement.corporateActionId
                createdAt = LocalDateTime.now(clock)
            }
        }
    }
}
