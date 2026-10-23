package dev.agner.portfolio.persistence.tax

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.tax.repository.IHarvestOptOutRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class HarvestOptOutRepository(
    private val clock: Clock,
) : IHarvestOptOutRepository {

    override suspend fun fetchAll(): Set<String> = transaction {
        HarvestOptOutEntity.all().map { it.ticker }.toSet()
    }

    // Idempotent both ways: opting out twice keeps one row, opting in twice removes nothing more.
    override suspend fun set(ticker: String, optedOut: Boolean) {
        transaction {
            val existing = HarvestOptOutEntity.find { HarvestOptOutTable.ticker eq ticker }
            if (optedOut) {
                if (existing.empty()) {
                    HarvestOptOutEntity.new {
                        this.ticker = ticker
                        createdAt = LocalDateTime.now(clock)
                    }
                }
            } else {
                existing.forEach { it.delete() }
            }
        }
    }
}
