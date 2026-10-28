package dev.agner.portfolio.persistence.tax

import dev.agner.portfolio.usecase.tax.model.CarryBucketOpening
import dev.agner.portfolio.usecase.tax.repository.ICarryBucketRepository
import kotlinx.datetime.YearMonth
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class TaxCarryBucketRepository : ICarryBucketRepository {

    override suspend fun fetchAll(): List<CarryBucketOpening> = transaction {
        TaxCarryBucketEntity.all().map {
            CarryBucketOpening(
                kind = it.kind,
                openingBalance = it.openingBalance,
                asOfPeriod = YearMonth.parse(it.asOfPeriod),
            )
        }
    }
}
