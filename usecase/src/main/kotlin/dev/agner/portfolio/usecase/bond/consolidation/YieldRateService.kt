package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.consolidation.model.BondContributionConsolidationContext.YieldRateContext
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.Bond.FixedRateBond
import dev.agner.portfolio.usecase.bond.model.Bond.FloatingRateBond
import dev.agner.portfolio.usecase.index.IndexValueService
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class YieldRateService(
    private val indexValueService: IndexValueService,
) {

    suspend fun buildRateFor(bond: Bond, startingAt: LocalDate) = when (bond) {
        is FloatingRateBond -> indexValueService.fetchAllBy(bond.indexId, startingAt)
            .associate { it.date to YieldRateContext(bond.value, it) }
        is FixedRateBond -> TODO()
    }
}
