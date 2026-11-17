package dev.agner.portfolio.persistence.broker

import dev.agner.portfolio.persistence.institution.InstitutionEntity
import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias
import dev.agner.portfolio.usecase.broker.repository.IBrokerAssetAliasRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BrokerAssetAliasRepository(
    private val clock: Clock,
) : IBrokerAssetAliasRepository {

    override suspend fun fetchByDescription(normalizedDescription: String): List<BrokerAssetAlias> = transaction {
        BrokerAssetAliasEntity
            .find { BrokerAssetAliasTable.normalizedDescription eq normalizedDescription }
            .map { it.toModel() }
    }

    // The owner's confirmed answer replaces that institution's previous one for the description, so
    // re-confirming a row cannot collide with the (institution, description) unique index. The
    // institution is matched in Kotlin because MySQL treats a null institution as distinct, which is
    // what makes two globals that disagree the ambiguity the resolver reports.
    override suspend fun save(
        institutionId: Int?,
        normalizedDescription: String,
        ticker: String,
    ): BrokerAssetAlias = transaction {
        val existing = BrokerAssetAliasEntity
            .find { BrokerAssetAliasTable.normalizedDescription eq normalizedDescription }
            .firstOrNull { it.institution?.id?.value == institutionId }

        val saved = existing?.apply { this.ticker = ticker } ?: BrokerAssetAliasEntity.new {
            this.institution = institutionId?.let { InstitutionEntity[it] }
            this.normalizedDescription = normalizedDescription
            this.ticker = ticker
            this.createdAt = LocalDateTime.now(clock)
        }
        saved.toModel()
    }
}
