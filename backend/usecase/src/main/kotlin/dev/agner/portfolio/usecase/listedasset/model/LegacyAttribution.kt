package dev.agner.portfolio.usecase.listedasset.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import java.math.BigDecimal

// The bulk legacy action's dry-run: every held Ticker that can enter its class's Manual Strategy,
// grouped per class, plus the Tickers it must skip and why. `quantity` is the slice's new total —
// the whole custody — never a delta, so a bucket Ticker with a buy and one with a sale both read as
// the custody they will hold after apply.
data class LegacyAttributionPreview(
    val groups: List<LegacyAttributionGroup>,
    val skips: List<LegacyAttributionSkip>,
)

data class LegacyAttributionGroup(
    val assetClass: AssetClass,
    val strategyId: Int,
    val strategyName: String,
    val adds: List<LegacyAttributionAdd>,
)

data class LegacyAttributionAdd(
    val ticker: String,
    val quantity: BigDecimal,
    val value: BigDecimal,
)

// Why a held Ticker stayed out of the bulk attribution. `message` carries the write path's reason
// when the skip only surfaces at apply time.
data class LegacyAttributionSkip(
    val ticker: String,
    val reason: LegacyAttributionSkipReason,
    val assetClass: AssetClass?,
    val message: String? = null,
)

enum class LegacyAttributionSkipReason {
    UNCLASSIFIED,
    NO_BUCKET,
    HAS_ATTRIBUTION,
    WRITE_FAILED,
}

// The apply response: each Ticker's slice is written independently, so a bad one is reported beside
// the ones that succeeded rather than aborting the run.
data class LegacyAttributionResult(
    val attributed: List<LegacyAttributionApplied>,
    val skipped: List<LegacyAttributionSkip>,
)

data class LegacyAttributionApplied(
    val ticker: String,
    val quantity: BigDecimal,
    val value: BigDecimal,
    val strategyId: Int,
    val strategyName: String,
)
