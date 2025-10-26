package dev.agner.portfolio.usecase.consolidation

import org.springframework.stereotype.Service

@Service
class ConsolidationService(
    private val consolidators: Set<ProductConsolidator<*>>,
) {

    suspend fun consolidateProduct(productId: Int, type: ProductType) =
        with(consolidators.first { it.type == type }) {
            consolidate(buildContext(productId))
        }
}
