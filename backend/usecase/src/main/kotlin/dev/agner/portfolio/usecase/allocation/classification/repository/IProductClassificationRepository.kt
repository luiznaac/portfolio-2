package dev.agner.portfolio.usecase.allocation.classification.repository

import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.consolidation.ProductType

interface IProductClassificationRepository {

    suspend fun fetchAll(): List<ProductClassification>

    /** Upserts by (productType, productId). */
    suspend fun save(classification: ProductClassification): ProductClassification

    suspend fun delete(productType: ProductType, productId: Int)
}
