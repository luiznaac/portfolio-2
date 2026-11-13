package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.institution.InstitutionEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeExecutionRange
import dev.agner.portfolio.usecase.listedasset.model.TradeKey
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerCursor
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerFilter
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.functions.math.AbsFunction
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.select
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

    override suspend fun fetchExecutionDateRange(): TradeExecutionRange? = transaction {
        val dates = TradeEntity.all().map { it.date }
        if (dates.isEmpty()) null else TradeExecutionRange(dates.min(), dates.max())
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

    // The page and its count are built from the same filter predicate, so they can never disagree
    // about what the filter means. Ordering by (date, id) descending makes the cursor a stable
    // boundary even when executions share a date, and the extra row tells the service whether
    // another page exists.
    override suspend fun fetchLedger(filter: TradeLedgerFilter): List<Trade> = transaction {
        TradeEntity.find { filter.condition() }
            .orderBy(TradeTable.date to SortOrder.DESC, TradeTable.id to SortOrder.DESC)
            .limit(filter.limit + 1)
            .map { it.toModel() }
    }

    // The aggregates are the filter's whole size, never the remaining page size, so the cursor is
    // dropped: neither "n de m" nor the total row may shrink as the owner pages forward. The
    // stored quantity is signed, so the financial value takes its magnitude before multiplying.
    override suspend fun countLedger(filter: TradeLedgerFilter): Int = transaction {
        TradeEntity.find { filter.copy(after = null).condition() }.count().toInt()
    }

    override suspend fun sumLedgerFinancial(filter: TradeLedgerFilter): BigDecimal = transaction {
        val financial = AbsFunction(TradeTable.quantity) times TradeTable.unitPrice
        val total = financial.sum()
        TradeTable.select(total)
            .where { filter.copy(after = null).condition() }
            .first()[total] ?: BigDecimal.ZERO
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

// The ledger filter as one predicate. The ticker is a case-insensitive substring, the date window is
// inclusive and the side is the sign of the stored quantity, so the read shares the table's own
// encoding. A filter with no values is the always-true id predicate, which keeps the builder total.
private fun TradeLedgerFilter.condition(): Op<Boolean> =
    listOfNotNull(
        ticker?.let { TradeTable.ticker.lowerCase() like "%${it.lowercase()}%" },
        from?.let { TradeTable.date greaterEq it },
        to?.let { TradeTable.date lessEq it },
        side?.let { it.condition() },
        after?.let { it.condition() },
    ).reduceOrNull { left, right -> left and right } ?: (TradeTable.id greater 0)

private fun TradeSide.condition(): Op<Boolean> =
    if (this == TradeSide.BUY) {
        TradeTable.quantity greater BigDecimal.ZERO
    } else {
        TradeTable.quantity less BigDecimal.ZERO
    }

private fun TradeLedgerCursor.condition(): Op<Boolean> =
    (TradeTable.date less date) or ((TradeTable.date eq date) and (TradeTable.id less id))
