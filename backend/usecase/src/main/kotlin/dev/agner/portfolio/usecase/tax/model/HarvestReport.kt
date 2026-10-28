package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The Impostos screen's harvest read: the open month's remaining exemption headroom, the pass's
// gate, its active and ignored suggestions, the FII report and the pairs already accepted.
data class HarvestReport(
    val period: YearMonth,
    val headroom: BigDecimal,
    val minimumLoss: BigDecimal,
    val suggestions: List<HarvestProposal>,
    val ignored: List<HarvestIgnored>,
    val fiiReport: List<FiiHarvestReport>,
    val pairs: List<HarvestPair>,
)
