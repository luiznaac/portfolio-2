package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

// No trades can predate the asset's own creation, so any date far enough in the past works as the
// opening bound of the first ticker-history entry.
private const val TICKER_HISTORY_OPENING_YEAR = 1970

@Component
class ListedAssetRepository(
    private val clock: Clock,
) : IListedAssetRepository {

    override suspend fun fetchAll() = transaction {
        ListedAssetEntity.all().map { it.toModel() }
    }

    override suspend fun fetchById(id: Int) = transaction {
        ListedAssetEntity.findById(id)?.toModel()
    }

    override suspend fun save(creation: ListedAssetCreation) = transaction {
        val now = LocalDateTime.now(clock)

        val entity = ListedAssetEntity.new {
            ticker = creation.ticker
            kind = creation.kind.name
            name = creation.name
            b3Identifier = creation.b3Identifier
            createdAt = now
        }

        ListedAssetTickerHistoryEntity.new {
            listedAsset = entity
            ticker = creation.ticker
            effectiveFrom = LocalDate(TICKER_HISTORY_OPENING_YEAR, 1, 1)
            effectiveTo = null
            createdAt = now
        }

        entity.toModel()
    }

    override suspend fun changeTicker(assetId: Int, newTicker: String, effectiveFrom: LocalDate) = transaction {
        val entity = ListedAssetEntity.findById(assetId)
            ?: throw IllegalArgumentException("Listed asset with ID $assetId not found")

        exec(
            """
            UPDATE listed_asset_ticker_history
            SET effective_to = '$effectiveFrom'
            WHERE listed_asset_id = $assetId AND effective_to IS NULL;
            """.trimIndent(),
        )

        ListedAssetTickerHistoryEntity.new {
            listedAsset = entity
            ticker = newTicker
            this.effectiveFrom = effectiveFrom
            effectiveTo = null
            createdAt = LocalDateTime.now(clock)
        }

        entity.ticker = newTicker
        entity.toModel()
    }

    override suspend fun resolveIdByTicker(ticker: String, date: LocalDate) = transaction {
        exec(
            """
            SELECT listed_asset_id
            FROM listed_asset_ticker_history
            WHERE ticker = '${ticker.replace("'", "''")}'
                AND effective_from <= '$date'
                AND (effective_to IS NULL OR effective_to >= '$date')
            LIMIT 1;
            """.trimIndent(),
        ) { resultSet ->
            if (resultSet.next()) resultSet.getInt("listed_asset_id") else null
        }
    }
}
