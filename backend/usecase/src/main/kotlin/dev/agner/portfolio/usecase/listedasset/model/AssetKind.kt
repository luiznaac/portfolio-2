package dev.agner.portfolio.usecase.listedasset.model

/**
 * What kind of listed asset this is. Distinct from [dev.agner.portfolio.usecase.consolidation.ProductType]
 * (technical wiring) and from an allocation class (a user policy, e.g. "Ações" vs "Real State" in the
 * portfolio spreadsheet) — a `FII` can be allocated as real estate or as fixed income, depending on the
 * user's own criteria; this enum only says how the asset trades on B3.
 */
enum class AssetKind {
    STOCK,
    FII,
    ETF,
    BDR,
}
