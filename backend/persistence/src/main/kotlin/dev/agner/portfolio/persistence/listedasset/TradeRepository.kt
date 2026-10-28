package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.institution.InstitutionEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class TradeRepository(
    private val clock: Clock,
) : ITradeRepository {

    // The side is folded into the sign of the stored quantity via `signedQuantity`.
    override suspend fun save(creation: TradeCreation, source: TradeSource, ordinal: Int): Trade = transaction {
        TradeEntity.new {
            ticker = creation.ticker
            date = creation.date
            venue = creation.venue
            quantity = signedQuantity(creation.side, creation.quantity)
            unitPrice = creation.unitPrice
            institution = InstitutionEntity[creation.institutionId]
            fee = creation.fee
            sourceType = source
            confirmedByB3 = false
            this.ordinal = ordinal
            absorbedQuantity = BigDecimal.ZERO
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun fetchAll(): List<Trade> = transaction {
        TradeEntity.all()
            .orderBy(TradeTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchById(id: Int): Trade? = transaction {
        TradeEntity.findById(id)?.toModel()
    }

    override suspend fun fetchAllBy(ticker: String): List<Trade> = transaction {
        TradeEntity.find { TradeTable.ticker eq ticker }
            .orderBy(TradeTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    // Quantity is compared in its signed form so the side is part of the tuple; MySQL compares the
    // DECIMAL numerically, so the stored scale (18,6) does not need to match the key's.
    override suspend fun fetchByKey(key: TradeKey): List<Trade> = transaction {
        val signed = signedQuantity(key.side, key.quantity)
        TradeEntity.find {
            (TradeTable.ticker eq key.ticker) and
                (TradeTable.date eq key.date) and
                (TradeTable.venue eq key.venue) and
                (TradeTable.quantity eq signed) and
                (TradeTable.unitPrice eq key.unitPrice) and
                (TradeTable.institutionId eq key.institutionId)
        }
            .orderBy(TradeTable.ordinal to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun confirmByB3(id: Int): Trade? = transaction {
        TradeEntity.findById(id)
            ?.apply { confirmedByB3 = true }
            ?.toModel()
    }

    override suspend fun fetchAllBySource(source: TradeSource): List<Trade> = transaction {
        TradeEntity.find { TradeTable.sourceType eq source }
            .orderBy(TradeTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun delete(id: Int): Boolean = transaction {
        val entity = TradeEntity.findById(id) ?: return@transaction false
        entity.delete()
        true
    }

    override suspend fun absorb(id: Int, remainingQuantity: BigDecimal, absorbedQuantity: BigDecimal): Trade? =
        transaction {
            TradeEntity.findById(id)
                ?.apply {
                    quantity = signedQuantity(toModel().side, remainingQuantity)
                    this.absorbedQuantity = absorbedQuantity
                }
                ?.toModel()
        }
}
