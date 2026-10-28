package dev.agner.portfolio.usecase.catalog.model

// The allocation class a listed asset belongs to. RENDA_FIXA is part of the shared class vocabulary
// but is unreachable from a listed-asset kind — fixed income is the consolidation engine's domain.
enum class AssetClass {
    ACOES,
    FIIS,
    RENDA_FIXA,
}

// A listed asset's allocation class. BDRs and ETFs allocate alongside stocks (Ações BR); an
// unclassified kind has no class yet, so the caller keeps the null rather than guessing.
fun AssetKind.toAssetClass(): AssetClass? = when (this) {
    AssetKind.STOCK, AssetKind.UNIT, AssetKind.ETF, AssetKind.BDR -> AssetClass.ACOES
    AssetKind.FII -> AssetClass.FIIS
    AssetKind.UNKNOWN -> null
}
