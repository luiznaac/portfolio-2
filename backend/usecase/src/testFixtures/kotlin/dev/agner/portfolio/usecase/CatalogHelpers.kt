package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// Shared builders for the catalog and quote tests: one row/entry shape, so a spec states only the
// fields it cares about instead of re-deriving the constructors in every file.

fun cotahistRow(
    ticker: String = "PETR4",
    shortName: String = ticker,
    bdiType: String = "02",
    isin: String? = null,
    date: String = "2026-10-14",
    closePrice: String = "10",
) = CotahistRow(
    ticker = ticker,
    shortName = shortName,
    bdiType = bdiType,
    isin = isin,
    date = LocalDate.parse(date),
    closePrice = BigDecimal(closePrice),
)

fun catalogEntry(
    ticker: String = "PETR4",
    shortName: String = ticker,
    bdiType: String = "02",
    isin: String? = null,
    assetKind: AssetKind = AssetKind.STOCK,
    assetClass: AssetClass? = AssetClass.ACOES,
    assetClassOverride: AssetClass? = null,
    firstSeen: String = "2026-01-02",
    lastSeen: String = "2026-10-01",
) = CatalogEntry(
    ticker = ticker,
    shortName = shortName,
    bdiType = bdiType,
    isin = isin,
    assetKind = assetKind,
    assetClass = assetClass,
    assetClassOverride = assetClassOverride,
    firstSeen = LocalDate.parse(firstSeen),
    lastSeen = LocalDate.parse(lastSeen),
)
