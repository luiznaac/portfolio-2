package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.Position

interface IPositionRepository {

    suspend fun fetchAll(): List<Position>

    suspend fun fetchBy(ticker: String): Position?

    // Upsert keyed on the ticker — a ticker has exactly one projected position.
    suspend fun save(position: Position)

    // Removes a projection row that a rename has superseded, so the old code does not linger beside
    // the canonical one. False when no position has that ticker.
    suspend fun delete(ticker: String): Boolean
}
