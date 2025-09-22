package dev.agner.portfolio.usecase.index.repository

import dev.agner.portfolio.usecase.index.model.Index

interface IIndexRepository {

    suspend fun fetchAllIndexes(): Set<Index>
}
