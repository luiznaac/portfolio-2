package dev.agner.portfolio.usecase.tax.model

// The pure harvest pass output: the active suggestions (which consumed the headroom), the ignored
// tickers (the owner's opt-outs, kept visible), and the FII report-only rows.
data class HarvestComputation(
    val suggestions: List<HarvestProposal>,
    val ignored: List<HarvestIgnored>,
    val fiiReport: List<FiiHarvestReport>,
)
