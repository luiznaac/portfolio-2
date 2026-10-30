package dev.agner.portfolio.persistence.benchmark

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkClose
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object BenchmarkCloseTable : IntIdTable("benchmark_close") {
    // `index` is taken by ColumnSet's index builder, so the property differs from the column name.
    val indexName = enumerationByName<BenchmarkIndex>("index", 10)
    val date = date("date")
    val level = decimal("level", 18, 6)

    // `source` is taken by ColumnSet, so the property differs from the column name.
    val sourceType = enumerationByName<BenchmarkSource>("source", 10)
    val fetchedAt = datetime("fetched_at")

    init { uniqueIndex(indexName, date) }
}

@OptIn(ExperimentalTime::class)
class BenchmarkCloseEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BenchmarkCloseEntity>(BenchmarkCloseTable)

    var index by BenchmarkCloseTable.indexName
    var date by BenchmarkCloseTable.date
    var level by BenchmarkCloseTable.level
    var sourceType by BenchmarkCloseTable.sourceType
    var fetchedAt by BenchmarkCloseTable.fetchedAt

    fun toModel() = BenchmarkClose(
        index = index,
        date = date,
        level = level,
        source = sourceType,
    )
}
