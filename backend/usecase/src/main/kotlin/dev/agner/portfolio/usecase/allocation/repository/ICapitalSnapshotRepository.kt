package dev.agner.portfolio.usecase.allocation.repository

import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshot
import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshotCreation

interface ICapitalSnapshotRepository {

    suspend fun fetchAll(): List<CapitalSnapshot>

    suspend fun fetchLast(): CapitalSnapshot?

    suspend fun save(creation: CapitalSnapshotCreation): CapitalSnapshot
}
