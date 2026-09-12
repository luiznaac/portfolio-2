package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.strategy.StrategyEditionAlreadyExistsException
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import dev.agner.portfolio.usecase.strategy.model.StrategyEdition
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.repository.IStrategyEditionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class StrategyEditionRepository(
    private val clock: Clock,
) : IStrategyEditionRepository {

    override suspend fun fetchByStrategyId(strategyId: Int): List<StrategyEdition> = transaction {
        val editions = StrategyEditionEntity.find { StrategyEditionTable.strategy eq strategyId }
            .orderBy(StrategyEditionTable.referenceDate to SortOrder.ASC)
            .toList()
        val targetsByEditionId = if (editions.isEmpty()) {
            emptyMap()
        } else {
            StrategyTargetEntity.find {
                StrategyTargetTable.strategyEdition inList editions.map { it.id }
            }.groupBy { it.strategyEdition.id.value }
        }

        editions.map { it.toModel(targetsByEditionId[it.id.value].orEmpty().map(StrategyTargetEntity::toModel)) }
    }

    override suspend fun strategyExists(strategyId: Int): Boolean = transaction {
        StrategyEntity.findById(strategyId) != null
    }

    override suspend fun exists(strategyId: Int, referenceDate: LocalDate): Boolean = transaction {
        StrategyEditionEntity.find {
            (StrategyEditionTable.strategy eq strategyId) and
                (StrategyEditionTable.referenceDate eq referenceDate)
        }.any()
    }

    override suspend fun save(creation: StrategyEditionCreation): StrategyEdition = try {
        transaction {
            val entity = StrategyEditionEntity.new {
                strategy = StrategyEntity.findById(creation.strategyId)
                    ?: throw StrategyNotFoundException(creation.strategyId)
                referenceDate = creation.referenceDate
                changesText = creation.changesText
                createdAt = LocalDateTime.now(clock)
            }

            StrategyTargetTable.batchInsert(creation.targets) { target ->
                this[StrategyTargetTable.strategyEdition] = entity.id
                this[StrategyTargetTable.ticker] = target.ticker
                this[StrategyTargetTable.weight] = target.weight
                this[StrategyTargetTable.rating] = target.rating
                this[StrategyTargetTable.targetPrice] = target.targetPrice
            }

            entity.toModel(creation.targets)
        }
    } catch (e: ExposedSQLException) {
        // uniqueIndex(strategy, referenceDate) is the concurrency guarantee for imports: the
        // service's exists() pre-check is only a friendly fast path. Translate only its
        // duplicate-key violation, leaving every other database failure untouched.
        if (isReferenceDateConflict(e.errorCode, e.sqlState)) {
            throw StrategyEditionAlreadyExistsException(creation.strategyId, creation.referenceDate.toString())
        }
        throw e
    }
}

private const val MYSQL_DUPLICATE_ENTRY = 1062
private const val DUPLICATE_KEY_SQL_STATE = "23000"

internal fun isReferenceDateConflict(errorCode: Int, sqlState: String?): Boolean =
    errorCode == MYSQL_DUPLICATE_ENTRY || sqlState == DUPLICATE_KEY_SQL_STATE

private fun StrategyEditionEntity.toModel(targets: List<StrategyTarget>) = StrategyEdition(
    id = id.value,
    strategyId = strategy.id.value,
    referenceDate = referenceDate,
    changesText = changesText,
    targets = targets,
)

private fun StrategyTargetEntity.toModel() = StrategyTarget(
    ticker = ticker,
    weight = weight,
    rating = rating,
    targetPrice = targetPrice,
)
