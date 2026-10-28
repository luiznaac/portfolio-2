package dev.agner.portfolio.usecase.tax.repository

import dev.agner.portfolio.usecase.tax.model.CarryBucketOpening

interface ICarryBucketRepository {

    // The seeded opening balances, one row per bucket that adoption filled. A bucket absent from
    // the list starts at zero and rolls its whole ledger history. Adoption writes the rows together
    // at a month close; that write flow is a later ticket, so until it lands the acceptance runbook
    // seeds the three rows directly.
    suspend fun fetchAll(): List<CarryBucketOpening>
}
