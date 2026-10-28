package dev.agner.portfolio.persistence.tax

import dev.agner.portfolio.persistence.portfolio.MonthEntity
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.tax.model.HarvestLeg
import dev.agner.portfolio.usecase.tax.model.HarvestPair
import dev.agner.portfolio.usecase.tax.model.HarvestPairCreation
import dev.agner.portfolio.usecase.tax.repository.IHarvestPairRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class HarvestPairRepository(
    private val clock: Clock,
) : IHarvestPairRepository {

    override suspend fun fetchBy(monthId: Int): List<HarvestPair> = transaction {
        HarvestPairEntity.find { HarvestPairTable.monthId eq monthId }
            .orderBy(HarvestPairTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(
        monthId: Int,
        creation: HarvestPairCreation,
        loss: BigDecimal,
    ): HarvestPair = transaction {
        HarvestPairEntity.new {
            month = MonthEntity[monthId]
            ticker = creation.ticker
            strategy = creation.strategyId?.let { StrategyEntity[it] }
            sellDate = creation.sellDate
            sellQuantity = creation.sellQuantity
            sellPrice = creation.sellPrice
            rebuyDate = creation.rebuyDate
            rebuyQuantity = creation.rebuyQuantity
            rebuyPrice = creation.rebuyPrice
            this.loss = loss
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    // The legs' amounts are derived from quantity × price on read, so a stored value can never
    // disagree with the legs it belongs to.
    private fun HarvestPairEntity.toModel() = HarvestPair(
        id = id.value,
        ticker = ticker,
        strategyId = strategy?.id?.value,
        strategy = strategy?.name,
        loss = loss,
        sell = HarvestLeg(
            date = sellDate,
            quantity = sellQuantity,
            price = sellPrice,
            amount = sellQuantity.multiply(sellPrice).defaultScale(),
        ),
        rebuy = HarvestLeg(
            date = rebuyDate,
            quantity = rebuyQuantity,
            price = rebuyPrice,
            amount = rebuyQuantity.multiply(rebuyPrice).defaultScale(),
        ),
    )
}
