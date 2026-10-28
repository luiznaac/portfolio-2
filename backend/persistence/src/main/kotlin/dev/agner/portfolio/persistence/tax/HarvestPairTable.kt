package dev.agner.portfolio.persistence.tax

import dev.agner.portfolio.persistence.portfolio.MonthEntity
import dev.agner.portfolio.persistence.portfolio.MonthTable
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.persistence.strategy.StrategyTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// An accepted harvest pair in an open month. The pair is a plan, not a trade: both legs are stored
// so the Impostos screen can show the D/D+1 step-up, and `loss` freezes the realized loss at
// acceptance. The strategy slice is optional because an unattributed position has none.
@OptIn(ExperimentalTime::class)
object HarvestPairTable : IntIdTable("harvest_pair") {
    val monthId = reference("month_id", MonthTable.id)
    val ticker = varchar("ticker", 12)
    val strategyId = reference("strategy_id", StrategyTable.id).nullable()
    val sellDate = date("sell_date")
    val sellQuantity = decimal("sell_quantity", 18, 6)
    val sellPrice = decimal("sell_price", 18, 6)
    val rebuyDate = date("rebuy_date")
    val rebuyQuantity = decimal("rebuy_quantity", 18, 6)
    val rebuyPrice = decimal("rebuy_price", 18, 6)
    val loss = decimal("loss", 18, 6)
    val createdAt = datetime("created_at")

    // One accepted pair per ticker in a month: a second acceptance is a corrected plan, not a
    // second pair, so the natural key keeps the ledger honest.
    init { uniqueIndex(monthId, ticker) }
}

@OptIn(ExperimentalTime::class)
class HarvestPairEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<HarvestPairEntity>(HarvestPairTable)

    var month by MonthEntity referencedOn HarvestPairTable.monthId
    var ticker by HarvestPairTable.ticker
    var strategy by StrategyEntity optionalReferencedOn HarvestPairTable.strategyId
    var sellDate by HarvestPairTable.sellDate
    var sellQuantity by HarvestPairTable.sellQuantity
    var sellPrice by HarvestPairTable.sellPrice
    var rebuyDate by HarvestPairTable.rebuyDate
    var rebuyQuantity by HarvestPairTable.rebuyQuantity
    var rebuyPrice by HarvestPairTable.rebuyPrice
    var loss by HarvestPairTable.loss
    var createdAt by HarvestPairTable.createdAt
}
