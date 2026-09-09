package dev.agner.portfolio.usecase.allocation.model

// User policy, not a property of the product — distinct from AssetKind (STOCK/FII/ETF/BDR) or
// ProductType (BOND/CHECKING_ACCOUNT/LISTED_ASSET). A credit-receivables FII behaves more like
// fixed income than real estate; this axis lets the user say so. See ProductClassification.
enum class AssetClass {
    ACOES,
    REAL_STATE,
    RENDA_FIXA,
    ALTERNATIVOS,
    CAIXA,
}
