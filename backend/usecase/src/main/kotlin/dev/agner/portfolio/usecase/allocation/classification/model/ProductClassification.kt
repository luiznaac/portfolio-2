package dev.agner.portfolio.usecase.allocation.classification.model

import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.consolidation.ProductType

// An explicit override of a product's AssetClass. No row means "use the default for its
// ProductType" (see AllocationService.defaultClassOf). Most products never need one; this exists
// for cases like reclassifying a credit-receivables FII as FIXED_INCOME rather than REAL_ESTATE.
data class ProductClassification(
    val productType: ProductType,
    val productId: Int,
    val assetClass: AssetClass,
)
