package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.SubClassTarget
import dev.agner.portfolio.usecase.portfolio.model.TargetLevel
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import dev.agner.portfolio.usecase.portfolio.repository.ITargetRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class TargetRepository(
    private val clock: Clock,
) : ITargetRepository {

    override suspend fun fetchByMonth(monthId: Int): Targets = transaction {
        val rows = TargetEntity.find { TargetTable.monthId eq monthId }.toList()

        Targets(
            classes = rows.filter { it.level == TargetLevel.CLASS }.map {
                ClassTarget(it.required(it.assetClass, "asset_class"), it.percentage)
            },
            strategies = rows.filter { it.level == TargetLevel.STRATEGY }.map {
                StrategyTarget(
                    it.required(it.assetClass, "asset_class"),
                    it.required(it.strategy, "strategy"),
                    it.percentage,
                )
            },
            subClasses = rows.filter { it.level == TargetLevel.SUB_CLASS }.map {
                SubClassTarget(it.required(it.subClass, "sub_class"), it.percentage)
            },
            tickers = rows.filter { it.level == TargetLevel.TICKER }.map {
                TickerTarget(
                    strategy = it.required(it.strategy, "strategy"),
                    ticker = it.required(it.ticker, "ticker"),
                    percentage = it.percentage,
                )
            },
        )
    }

    override suspend fun replaceAll(monthId: Int, targets: Targets) {
        transaction {
            val existing = TargetEntity.find { TargetTable.monthId eq monthId }.toList()
            for (row in existing) {
                row.delete()
            }

            val month = MonthEntity[monthId]
            val createdAt = LocalDateTime.now(clock)

            for (target in targets.classes) {
                newTarget(month, TargetLevel.CLASS, createdAt).apply {
                    assetClass = target.assetClass
                    percentage = target.percentage
                }
            }
            for (target in targets.strategies) {
                newTarget(month, TargetLevel.STRATEGY, createdAt).apply {
                    assetClass = target.assetClass
                    strategy = target.strategy
                    percentage = target.percentage
                }
            }
            for (target in targets.subClasses) {
                newTarget(month, TargetLevel.SUB_CLASS, createdAt).apply {
                    subClass = target.subClass
                    percentage = target.percentage
                }
            }
            for (target in targets.tickers) {
                newTarget(month, TargetLevel.TICKER, createdAt).apply {
                    strategy = target.strategy
                    ticker = target.ticker
                    percentage = target.percentage
                }
            }
        }
    }
}

private fun newTarget(month: MonthEntity, level: TargetLevel, createdAt: LocalDateTime) = TargetEntity.new {
    this.month = month
    this.level = level
    this.createdAt = createdAt
}

// A stored row's level says which key columns must be present; a null there means a row the
// repository wrote is malformed, not an optional field.
private fun <T> TargetEntity.required(value: T?, column: String): T =
    value ?: error("target ${id.value} (level $level) is missing $column")
