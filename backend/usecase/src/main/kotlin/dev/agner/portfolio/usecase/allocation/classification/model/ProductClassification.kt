package dev.agner.portfolio.usecase.allocation.classification.model

import dev.agner.portfolio.usecase.allocation.model.AssetClass
import dev.agner.portfolio.usecase.consolidation.ProductType

// An explicit override of a product's AssetClass. Absence of a row means "use the default for
// its ProductType" (see AllocationService.defaultClassOf) — most products never need one; this
// exists for the cases the plan calls out explicitly, like reclassifying a credit-receivables FII
// as RENDA_FIXA instead of the REAL_STATE default.
data class ProductClassification(
    val productType: ProductType,
    val productId: Int,
    val assetClass: AssetClass,
)
