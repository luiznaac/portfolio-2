package dev.agner.portfolio.usecase.listedasset.repository

import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import java.math.BigDecimal

interface IGapRepository {

    suspend fun save(creation: GapCreation): Gap

    suspend fun fetchAll(): List<Gap>

    // The unresolved records for one ticker — what the replay folds in.
    suspend fun fetchOpenBy(ticker: String): List<Gap>

    // Marks the record retired by a true record. Null when no GAP has that id.
    suspend fun markReplaced(id: Int): Gap?

    // Corrects an open record's placeholder price (a sale's proceeds, a buy's market estimate),
    // keeping it open. Null when no GAP has that id.
    suspend fun price(id: Int, unitPrice: BigDecimal): Gap?
}
