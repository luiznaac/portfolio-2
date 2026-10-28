package dev.agner.portfolio.usecase.catalog.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One à-vista record parsed from a COTAHIST file, before classification. The BDI code is kept raw
// so the catalog layer — not the format parser — owns the kind/class mapping. A COTAHIST row feeds
// both the catalog and the EOD quote store.
data class CotahistRow(
    val ticker: String,
    val shortName: String,
    val bdiType: String,
    val isin: String?,
    val date: LocalDate,
    val closePrice: BigDecimal,
    // B3's "Especificação do papel" — the only field that separates a unit (UNT) from a stock,
    // since both share BDI 02. Kept raw so the classification stays in AssetKind.
    val specification: String? = null,
) {
    val assetKind: AssetKind get() = AssetKind.fromBdiType(bdiType, specification)

    fun toCatalogEntry() = CatalogEntry(
        ticker = ticker,
        shortName = shortName,
        bdiType = bdiType,
        isin = isin,
        assetKind = assetKind,
        assetClass = assetKind.toAssetClass(),
        firstSeen = date,
        lastSeen = date,
    )
}
