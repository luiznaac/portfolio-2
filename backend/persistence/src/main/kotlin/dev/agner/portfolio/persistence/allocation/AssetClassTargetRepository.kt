package dev.agner.portfolio.persistence.allocation

import dev.agner.portfolio.usecase.allocation.model.AssetClassTargetCreation
import dev.agner.portfolio.usecase.allocation.repository.IAssetClassTargetRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AssetClassTargetRepository(
    private val clock: Clock,
) : IAssetClassTargetRepository {

    override suspend fun fetchAll() = transaction {
        AssetClassTargetEntity.all()
            .orderBy(AssetClassTargetTable.effectiveFrom to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchCurrent(date: LocalDate) = transaction {
        AssetClassTargetEntity.find { AssetClassTargetTable.effectiveFrom lessEq date }
            .orderBy(AssetClassTargetTable.effectiveFrom to SortOrder.ASC)
            .map { it.toModel() }
            .groupBy { it.assetClass }
            .values
            .map { it.last() }
    }

    override suspend fun save(creation: AssetClassTargetCreation) = transaction {
        AssetClassTargetEntity.new {
            assetClass = creation.assetClass.name
            weight = creation.weight
            effectiveFrom = creation.effectiveFrom
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
