package dev.agner.portfolio.usecase.index

import dev.agner.portfolio.usecase.index.repository.IIndexRepository
import org.springframework.stereotype.Service

@Service
class IndexService(
    private val indexRepository: IIndexRepository,
) {

    suspend fun fetchAllIndexes() = indexRepository.fetchAllIndexes()
}
