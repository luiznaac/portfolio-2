package dev.agner.portfolio.usecase.allocation.model

/**
 * User policy, not a property of the product — distinct from
 * [dev.agner.portfolio.usecase.listedasset.model.AssetKind] (STOCK/FII/ETF/BDR) and from
 * [dev.agner.portfolio.usecase.consolidation.ProductType]. A credit-receivables FII behaves more
 * like fixed income than real estate; this axis lets the user say so.
 */
enum class AssetClass {
    STOCKS,
    REAL_ESTATE,
    FIXED_INCOME,
    ALTERNATIVES,
    CASH,
}
