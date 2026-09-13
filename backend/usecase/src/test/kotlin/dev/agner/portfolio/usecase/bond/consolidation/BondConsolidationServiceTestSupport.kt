package dev.agner.portfolio.usecase.bond.consolidation

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.bond.repository.IBondOrderStatementRepository
import dev.agner.portfolio.usecase.index.IndexValueService
import io.mockk.mockk
import java.time.Clock

data class BondConsolidationServiceTestSupport(
    val repository: IBondOrderStatementRepository,
    val bondService: BondService,
    val bondOrderService: BondOrderService,
    val indexValueService: IndexValueService,
    val contributionConsolidator: BondContributionConsolidator,
    val positionService: BondPositionService,
    val clock: Clock,
) {
    val service = BondConsolidationService(
        repository,
        bondOrderService,
        YieldRateService(indexValueService, clock),
        contributionConsolidator,
        clock,
    )
    val consolidator = BondConsolidator(repository, bondService, bondOrderService, service, positionService)
}

fun bondConsolidationServiceTestSupport() = BondConsolidationServiceTestSupport(
    repository = mockk(),
    bondService = mockk(),
    bondOrderService = mockk(),
    indexValueService = mockk(),
    contributionConsolidator = mockk(),
    positionService = mockk(relaxUnitFun = true),
    clock = mockk(),
)
