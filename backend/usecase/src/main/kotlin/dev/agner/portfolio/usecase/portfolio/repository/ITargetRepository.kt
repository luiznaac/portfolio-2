package dev.agner.portfolio.usecase.portfolio.repository

import dev.agner.portfolio.usecase.portfolio.model.Targets

interface ITargetRepository {

    // The month's whole chain; EMPTY when none was entered.
    suspend fun fetchByMonth(monthId: Int): Targets

    // Replaces the whole chain for a month. The entry form always submits every rung, so the write
    // is a delete-then-insert inside one transaction and no per-row diff is needed.
    suspend fun replaceAll(monthId: Int, targets: Targets)
}
