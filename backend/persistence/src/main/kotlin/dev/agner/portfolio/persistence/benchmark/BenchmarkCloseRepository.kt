package dev.agner.portfolio.persistence.benchmark

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkClose
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.repository.IBenchmarkCloseRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BenchmarkCloseRepository(
    private val clock: Clock,
) : IBenchmarkCloseRepository {

    // Keyed on (index, date): a refetch overwrites the stored point and records when it was fetched.
    // The conflict keys are left implicit — MySQL's ON DUPLICATE KEY UPDATE uses the table's unique
    // keys (here the (index, date) unique index) and rejects an explicit conflict-column list.
    override suspend fun upsertAll(closes: List<BenchmarkClose>): Unit = transaction {
        if (closes.isEmpty()) return@transaction

        BenchmarkCloseTable.batchUpsert(closes) { close ->
            this[BenchmarkCloseTable.indexName] = close.index
            this[BenchmarkCloseTable.date] = close.date
            this[BenchmarkCloseTable.level] = close.level
            this[BenchmarkCloseTable.sourceType] = close.source
            this[BenchmarkCloseTable.fetchedAt] = LocalDateTime.now(clock)
        }
    }

    override suspend fun fetch(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkClose> = transaction {
        BenchmarkCloseEntity.find {
            (BenchmarkCloseTable.indexName eq index) and
                (BenchmarkCloseTable.date greaterEq from) and
                (BenchmarkCloseTable.date lessEq to)
        }
            .orderBy(BenchmarkCloseTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }
}
