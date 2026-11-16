package dev.agner.portfolio.persistence.broker

import dev.agner.portfolio.persistence.institution.InstitutionEntity
import dev.agner.portfolio.persistence.institution.InstitutionTable
import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The confirmed broker description → ticker mapping. `institution_id` is null for the global
// fallback, so a broker without an alias of its own still resolves. MySQL's unique index treats
// nulls as distinct, which is deliberate: two globals that name different tickers are the
// ambiguity the resolver reports, never a silent overwrite.
@OptIn(ExperimentalTime::class)
object BrokerAssetAliasTable : IntIdTable("broker_asset_alias") {
    val institutionId = reference("institution_id", InstitutionTable.id).nullable()
    val normalizedDescription = varchar("normalized_description", 255)
    val ticker = varchar("ticker", 12)
    val createdAt = datetime("created_at")

    init { uniqueIndex(institutionId, normalizedDescription) }
}

@OptIn(ExperimentalTime::class)
class BrokerAssetAliasEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BrokerAssetAliasEntity>(BrokerAssetAliasTable)

    var institution by InstitutionEntity optionalReferencedOn BrokerAssetAliasTable.institutionId
    var normalizedDescription by BrokerAssetAliasTable.normalizedDescription
    var ticker by BrokerAssetAliasTable.ticker
    var createdAt by BrokerAssetAliasTable.createdAt

    fun toModel() = BrokerAssetAlias(
        id = id.value,
        institutionId = institution?.id?.value,
        normalizedDescription = normalizedDescription,
        ticker = ticker,
    )
}
