package dev.agner.portfolio.persistence.broker

import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias
import dev.agner.portfolio.usecase.broker.repository.IBrokerAssetAliasRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class BrokerAssetAliasRepository : IBrokerAssetAliasRepository {

    override suspend fun fetchByDescription(normalizedDescription: String): List<BrokerAssetAlias> = transaction {
        BrokerAssetAliasEntity
            .find { BrokerAssetAliasTable.normalizedDescription eq normalizedDescription }
            .map { it.toModel() }
    }
}
