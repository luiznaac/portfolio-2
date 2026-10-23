package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class GapRepository(
    private val clock: Clock,
) : IGapRepository {

    override suspend fun save(creation: GapCreation): Gap = transaction {
        GapEntity.new {
            ticker = creation.ticker
            date = creation.date
            quantity = creation.quantity
            status = GapStatus.OPEN
            when (creation) {
                is GapCreation.MissingBuy -> {
                    side = TradeSide.BUY
                    unitPrice = creation.unitPrice
                    tradeWatermark = creation.tradeWatermark
                    actionWatermark = creation.actionWatermark
                }

                is GapCreation.MissingSale -> {
                    side = TradeSide.SELL
                    unitPrice = null
                    tradeWatermark = creation.tradeWatermark
                    actionWatermark = creation.actionWatermark
                }
            }
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun fetchAll(): List<Gap> = transaction {
        GapEntity.all()
            .orderBy(GapTable.date to SortOrder.ASC, GapTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchOpenBy(ticker: String): List<Gap> = transaction {
        GapEntity.find { (GapTable.ticker eq ticker) and (GapTable.status eq GapStatus.OPEN) }
            .orderBy(GapTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun markReplaced(id: Int): Gap? = transaction {
        GapEntity.findById(id)
            ?.apply { status = GapStatus.REPLACED }
            ?.toModel()
    }

    // Both variants can be priced: a missing sale needs its proceeds, while a missing buy's market
    // placeholder can be corrected by the owner before the true trade arrives.
    override suspend fun price(id: Int, unitPrice: BigDecimal): Gap? = transaction {
        GapEntity.findById(id)
            ?.apply { this.unitPrice = unitPrice }
            ?.toModel()
    }
}
