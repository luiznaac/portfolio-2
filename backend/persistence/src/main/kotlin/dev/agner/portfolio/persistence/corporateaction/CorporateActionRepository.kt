package dev.agner.portfolio.persistence.corporateaction

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.BonusCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.ReverseSplitCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.SplitCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.TickerChangeCreation
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class CorporateActionRepository(
    private val clock: Clock,
) : ICorporateActionRepository {

    override suspend fun fetchByAssetId(assetId: Int) = transaction {
        CorporateActionEntity.find { CorporateActionTable.listedAsset eq assetId }
            .orderBy(CorporateActionTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(assetId: Int, creation: CorporateActionCreation) = transaction {
        val asset = ListedAssetEntity.findById(assetId)
            ?: throw IllegalArgumentException("Listed asset with ID $assetId not found")

        CorporateActionEntity.new {
            listedAsset = asset
            date = creation.date
            createdAt = LocalDateTime.now(clock)

            when (creation) {
                is SplitCreation -> {
                    type = "SPLIT"
                    ratio = creation.ratio
                }
                is ReverseSplitCreation -> {
                    type = "REVERSE_SPLIT"
                    ratio = creation.ratio
                }
                is BonusCreation -> {
                    type = "BONUS"
                    ratio = creation.ratio
                    valuePerNewShare = creation.valuePerNewShare
                }
                is TickerChangeCreation -> {
                    type = "TICKER_CHANGE"
                    newTicker = creation.newTicker
                }
            }
        }.toModel()
    }
}
