package dev.agner.portfolio.usecase.allocation

import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.allocation.classification.repository.IProductClassificationRepository
import dev.agner.portfolio.usecase.allocation.model.AssetClassTargetCreation
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTargetCreation
import dev.agner.portfolio.usecase.allocation.repository.IAssetClassTargetRepository
import dev.agner.portfolio.usecase.allocation.repository.IFixedIncomeSubClassTargetRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component

/**
 * The user's allocation policy: class targets, fixed-income sub-class targets and explicit product
 * classifications — the three configuration axes [AllocationService] reads to build a plan.
 */
@Component
class AllocationTargetsProvider(
    private val classTargetRepository: IAssetClassTargetRepository,
    private val subClassTargetRepository: IFixedIncomeSubClassTargetRepository,
    private val classificationRepository: IProductClassificationRepository,
) {

    suspend fun fetchCurrentClassTargets(date: LocalDate) = classTargetRepository.fetchCurrent(date)

    suspend fun fetchCurrentSubClassTargets(date: LocalDate) = subClassTargetRepository.fetchCurrent(date)

    suspend fun setClassTarget(creation: AssetClassTargetCreation) = classTargetRepository.save(creation)

    suspend fun fetchClassTargetHistory() = classTargetRepository.fetchAll()

    suspend fun setFixedIncomeSubClassTarget(creation: FixedIncomeSubClassTargetCreation) =
        subClassTargetRepository.save(creation)

    suspend fun fetchFixedIncomeSubClassTargetHistory() = subClassTargetRepository.fetchAll()

    suspend fun classify(classification: ProductClassification) = classificationRepository.save(classification)

    suspend fun fetchClassifications() = classificationRepository.fetchAll()
}
